#!/usr/bin/env bb
;; extract_syntax.bb
;; Parses all .cljd files in src/, extracts every unique piece of English
;; syntax, and groups them by import. Uses edamame to parse actual
;; S-expressions so parent/child relationships are accurate.

(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[cheshire.core :as json]
         '[edamame.core :as e])

;; ── File discovery ──────────────────────────────────────────────

(defn find-cljd-files [dir]
  (->> (file-seq (io/file dir))
       (filter #(str/ends-with? (.getName %) ".cljd"))
       (sort-by str)))

;; ── Extract require alias→import mapping (regex, pre-parse) ─────

(defn extract-requires [raw]
  (let [dart (re-seq #"\[\"([^\"]+)\"\s+:as\s+(\w+)\]" raw)
        clj  (re-seq #"\[([a-zA-Z][\w\.\-]+)\s+:as\s+(\w+)\]" raw)]
    (merge
     (into {} (for [[_ imp a] dart] [a imp]))
     (into {} (for [[_ imp a] clj] [a imp])))))

(defn external? [imp]
  (or (str/starts-with? imp "package:")
      (str/starts-with? imp "dart:")
      (= imp "cljd.flutter")
      (= imp "clojure.string")))

;; ── Parse a .cljd file into forms ───────────────────────────────

(defn safe-parse [raw]
  (try
    (e/parse-string-all raw
      {:all true
       :read-cond :allow
       :regex true
       :fn true
       :quote true
       :deref true
       :syntax-quote {:resolve-symbol identity}
       :var true
       :end-location false
       ;; Allow reader metadata like ^:async
       :auto-resolve {:current (symbol "")}
       :readers (fn [_tag] identity)})
    (catch Exception ex
      (binding [*out* *err*]
        (println "  Parse warning:" (.getMessage ex)))
      nil)))

;; ── Walk forms and extract symbols ──────────────────────────────

(defn resolve-alias
  "Given a symbol like m/Scaffold, return [alias class-or-fn].
   Given m.Colors/green, return [alias 'Colors.green']."
  [sym]
  (let [s (str sym)]
    ;; alias.Class/member  →  nested static access
    (when-let [[_ a cls mem] (re-matches #"(\w+)\.([A-Z][\w]+)/(\w+)" s)]
      {:alias a :english (str cls "." mem) :type "static-access"})
    ;; Or alias/Name
    ))

(defn process-form
  "Recursively walk a parsed form. Returns collected entries.
   `alias->import` maps alias strings to import strings.
   `parent` is the nearest enclosing constructor name (or nil)."
  [form alias->import parent]
  (cond
    ;; A symbol like m/Scaffold or convert/jsonDecode
    (symbol? form)
    (let [s   (str form)
          ns- (namespace form)
          n   (name form)]
      (cond
        ;; Qualified: alias/Name
        (and ns- (not (str/includes? ns- ".")))
        (let [imp (get alias->import ns-)]
          (if (and imp (external? imp))
            ;; It's from an external import
            (let [is-ctor? (Character/isUpperCase (first n))]
              [{:import imp
                :english n
                :type (if is-ctor? "constructor" "function")}])
            ;; Internal project ref or no import → core
            []))

        ;; Nested: alias.Class/member  (edamame parses these oddly)
        ;; The namespace will contain dots
        (and ns- (str/includes? ns- "."))
        (if-let [[_ a cls] (re-matches #"(\w+)\.(.+)" ns-)]
          (let [imp (get alias->import a)]
            (if (and imp (external? imp))
              [{:import imp
                :english (str cls "." n)
                :type "static-access"}]
              []))
          [])

        ;; Unqualified symbol
        :else
        []))

    ;; A keyword like :watch, :managed, :keys
    (keyword? form)
    (let [k (str form)]
      (if (#{":watch" ":managed" ":keys" ":let" ":bind" ":context"
             ":else" ":as" ":require"} k)
        ;; we'll collect these as core
        []  ;; these are structural, not really translatable
        []))

    ;; A list (function call or special form)
    (and (seq? form) (seq form))
    (let [head      (first form)
          head-str  (when (symbol? head) (str head))
          ;; Is the head an unqualified symbol? → core form
          core-entry (when (and (symbol? head) (nil? (namespace head)))
                       {:english head-str :group :core})
          ;; Is the head a qualified constructor? Figure out new parent
          new-parent (when (and (symbol? head) (namespace head))
                       (let [ns-  (namespace head)
                             n    (name head)
                             alias-str (first (str/split ns- #"\." 2))
                             imp  (get alias->import alias-str)]
                         (when (and imp (external? imp)
                                    (Character/isUpperCase (first n)))
                           n)))
          effective-parent (or new-parent parent)
          ;; Collect .paramName entries from rest of form
          ;; In ClojureDart, named params appear as symbols like .body, .title
          dot-params (for [item (rest form)
                           :when (symbol? item)
                           :let [s (str item)]
                           :when (and (str/starts-with? s ".")
                                      (> (count s) 1)
                                      (Character/isLowerCase (nth s 1)))
                           ;; Find which import it belongs to via effective-parent
                           ;; The parent constructor tells us the import
                           :let [parent-imp (when effective-parent
                                             ;; search all entries to find the import of the parent
                                             ;; Actually, we know the import from the call context
                                             nil)]]
                      ;; We'll associate params with the head's import
                      {:english s :type "named-param" :parent effective-parent})
          ;; The import for .params comes from the head's import
          head-imp (when (and (symbol? head) (namespace head))
                     (let [alias-str (first (str/split (namespace head) #"\." 2))]
                       (get alias->import alias-str)))
          dot-entries (when (and head-imp (external? head-imp))
                        (for [d dot-params]
                          (assoc d :import head-imp)))
          ;; Recurse into all sub-forms
          sub-results (mapcat #(process-form % alias->import effective-parent)
                              (rest form))]
      (concat
       (when core-entry [core-entry])
       (process-form head alias->import parent) ;; process head as symbol
       (or dot-entries [])
       sub-results))

    ;; A vector, map, or set — recurse into contents
    (vector? form)
    (mapcat #(process-form % alias->import parent) form)

    (map? form)
    (mapcat #(process-form % alias->import parent)
            (interleave (keys form) (vals form)))

    (set? form)
    (mapcat #(process-form % alias->import parent) form)

    ;; Anything else (numbers, strings, etc.) — nothing to extract
    :else []))

;; ── Process one file ────────────────────────────────────────────

(defn process-file [f]
  (let [raw          (slurp f)
        alias->import (extract-requires raw)
        forms         (safe-parse raw)]
    (binding [*out* *err*]
      (println (str "  " f)))
    (if forms
      (mapcat #(process-form % alias->import nil) forms)
      [])))

;; ── Aggregate ───────────────────────────────────────────────────

(defn make-group-id [imp]
  (-> imp
      (str/replace #"package:" "")
      (str/replace #"\.dart$" "")
      (str/replace #"[^a-zA-Z0-9]+" "-")
      str/lower-case))

(defn aggregate [all-entries]
  (let [;; Split into core vs imported
        {core-raw :core imported-raw nil}
        (group-by #(when (= (:group %) :core) :core) all-entries)

        ;; Core: unique unqualified forms
        core-forms (->> core-raw
                        (map :english)
                        (remove nil?)
                        distinct
                        sort)

        ;; Imported: group by import
        by-import (->> (remove :group all-entries)  ;; remove core entries
                       (filter :import)
                       (group-by :import))]

    {:groups
     (into
      ;; Core group first
      [{:group_id "core"
        :import nil
        :entries (mapv (fn [e] {:english e :irish ""}) core-forms)}]

      ;; Then one group per external import
      (->> by-import
           (filter (fn [[imp _]] (external? imp)))
           (sort-by key)
           (mapv (fn [[imp entries]]
                   {:group_id (make-group-id imp)
                    :import imp
                    :entries (->> entries
                                  (map #(-> (dissoc % :import :group)
                                            (assoc :irish "")))
                                  ;; Deduplicate by [english type parent]
                                  (reduce (fn [acc e]
                                            (let [k (select-keys e [:english :type :parent])]
                                              (if (contains? (::seen (meta acc)) k)
                                                acc
                                                (with-meta (conj acc e)
                                                           {::seen (conj (::seen (meta acc) #{}) k)}))))
                                          (with-meta [] {::seen #{}}))
                                  vec
                                  (sort-by (juxt :type :parent :english))
                                  vec)}))))}))

;; ── Main ────────────────────────────────────────────────────────

(let [files   (find-cljd-files (io/file "src"))
      _       (binding [*out* *err*] (println (str "Found " (count files) " .cljd files:")))
      entries (mapcat process-file files)
      output  (aggregate (vec entries))
      json-str (json/generate-string output {:pretty true})]
  (spit "draíocht_syntax.json" json-str)
  (binding [*out* *err*]
    (println "\nWrote draíocht_syntax.json")
    (doseq [g (:groups output)]
      (println (str "  " (:group_id g) ": " (count (:entries g)) " entries")))))
