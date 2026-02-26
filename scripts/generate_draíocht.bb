#!/usr/bin/env bb
;; generate_draíocht.bb
;; Reads draíocht_syntax.json and generates .cljd wrapper files
;; in Generated_Draíocht/, one per import group.
;; Irish names are placeholder: TRANSLATE_<english>

(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[cheshire.core :as json])

;; ── Load JSON ───────────────────────────────────────────────────

(def data (json/parse-string (slurp "draíocht_syntax.json") true))

;; ── Helpers ─────────────────────────────────────────────────────

(defn translate [english]
  (str "TRANSLATE_" (-> english
                        (str/replace "." "_")
                        (str/replace "?" "_QMARK")
                        (str/replace "!" "_BANG")
                        (str/replace ">" "_GT")
                        (str/replace "<" "_LT")
                        (str/replace "=" "_EQ")
                        (str/replace "*" "_STAR")
                        (str/replace "^" "_CARET")
                        (str/replace ":" "_COLON")
                        (str/replace "-" "_")
                        (str/replace " " "_"))))

(defn file-name [group-id]
  (str (str/replace group-id "-" "_") ".cljd"))

(defn ns-name [group-id]
  (str "Generated-Draíocht." (str/replace group-id "-" "_")))

(defn alias-for-import [imp]
  (cond
    (str/includes? imp "material.dart")           "m"
    (str/includes? imp "services.dart")            "svc"
    (str/includes? imp "audioplayers")             "audio"
    (str/includes? imp "shared_preferences")       "store"
    (str/includes? imp "dart:convert")             "convert"
    (str/includes? imp "dart:async")               "async"
    (= imp "cljd.flutter")                         "f"
    (= imp "clojure.string")                       "s"
    :else                                          "lib"))

;; ── Build require form ──────────────────────────────────────────

(defn require-form [imp]
  (if (or (str/starts-with? imp "package:")
          (str/starts-with? imp "dart:"))
    (str "[\"" imp "\" :as " (alias-for-import imp) "]")
    (str "[" imp " :as " (alias-for-import imp) "]")))

;; ── Resolve how to call something in the generated code ─────────

(defn qualified-name [imp english]
  (let [a (alias-for-import imp)]
    (cond
      ;; Static access: Colors.green → m.Colors/green
      (and (re-find #"\." english)
           (Character/isUpperCase (first english)))
      (let [parts (str/split english #"\." 2)
            cls   (first parts)
            mem   (second parts)]
        ;; Could be nested like Colors.red.shade100
        (if (and mem (re-find #"\." mem))
          ;; Multi-level: m.Colors/red then .shade100
          (let [sub-parts (str/split mem #"\." 2)]
            (str a "." cls "/" (first sub-parts)))
          (str a "." cls "/" mem)))

      ;; Constructor: Scaffold → m/Scaffold
      (Character/isUpperCase (first english))
      (str a "/" english)

      ;; Function: runApp → m/runApp
      :else
      (str a "/" english))))

;; ── Generate code for each entry type ───────────────────────────

(defn gen-constructor-macro
  "Generate a macro for a constructor, including its named params."
  [imp entry params]
  (let [english-name (:english entry)
        irish-name   (translate english-name)
        qname        (qualified-name imp english-name)
        param-keys   (map (fn [p]
                            {:english-key (subs (:english p) 1) ;; remove leading .
                             :irish-key   (translate (subs (:english p) 1))
                             :dot-param   (:english p)})
                          params)]
    (if (seq param-keys)
      ;; Macro with keyword args
      (str "(defmacro " irish-name " [& {:keys ["
           (str/join " " (map :irish-key param-keys))
           "] :as opts}]\n"
           "  `(" qname "\n"
           (str/join "\n"
                     (map (fn [pk]
                            (str "    ~@(when (contains? opts :" (:irish-key pk) ") "
                                 "['" (:dot-param pk) " " (:irish-key pk) "])"))
                          param-keys))
           "))\n")
      ;; Constructor with no known params — passthrough
      (str "(defmacro " irish-name " [& args]\n"
           "  `(" qname " ~@args))\n"))))

(defn gen-static-access-def [imp entry]
  (let [english (:english entry)
        irish   (translate english)
        qname   (qualified-name imp english)]
    (str "(def " irish " " qname ")\n")))

(defn gen-function-macro [imp entry]
  (let [english (:english entry)
        irish   (translate english)
        qname   (qualified-name imp english)]
    (str "(defmacro " irish " [& args]\n"
         "  `(" qname " ~@args))\n")))

(defn gen-core-macro [entry]
  (let [english (:english entry)
        irish   (translate english)]
    ;; Some core entries start with . or special chars
    (if (str/starts-with? english ".")
      ;; Method call — def alias not really possible, skip or note
      (str ";; " english " — method call, translate in context\n"
           ";; (def " irish " ...)\n")
      ;; Regular form
      (str "(defmacro " irish " [& args]\n"
           "  `(" english " ~@args))\n"))))

;; ── Generate one file ───────────────────────────────────────────

(defn generate-group-file [group]
  (let [gid     (:group_id group)
        imp     (:import group)
        entries (:entries group)
        ;; Split entries by type
        by-type (group-by :type entries)
        constructors (get by-type "constructor" [])
        statics      (get by-type "static-access" [])
        functions    (get by-type "function" [])
        params       (get by-type "named-param" [])
        ;; Group params by parent constructor
        params-by-parent (group-by :parent params)
        ;; For core group, all entries have no type
        core-entries (when (nil? imp)
                       (filter #(nil? (:type %)) entries))]

    (str ";; Generated by generate_draíocht.bb\n"
         ";; Group: " gid "\n"
         (when imp (str ";; Import: " imp "\n"))
         ";;\n"
         ";; Replace TRANSLATE_<english> with Irish translations.\n\n"

         ;; Namespace
         "(ns " (ns-name gid) "\n"
         (if imp
           (str "  (:require " (require-form imp) "))\n")
           ")\n")
         "\n"

         ;; ── Constructors (with their params) ──
         (when (seq constructors)
           (str ";; ── Constructors ──\n\n"
                (str/join "\n"
                          (map (fn [c]
                                 (gen-constructor-macro
                                  imp c
                                  (get params-by-parent (:english c) [])))
                               constructors))
                "\n"))

         ;; ── Static access ──
         (when (seq statics)
           (str ";; ── Static Values ──\n\n"
                (str/join "\n" (map #(gen-static-access-def imp %) statics))
                "\n"))

         ;; ── Functions ──
         (when (seq functions)
           (str ";; ── Functions ──\n\n"
                (str/join "\n" (map #(gen-function-macro imp %) functions))
                "\n"))

         ;; ── Core entries (no import) ──
         (when (seq core-entries)
           (str ";; ── Core Forms ──\n\n"
                (str/join "\n" (map gen-core-macro core-entries))
                "\n")))))

;; ── Main ────────────────────────────────────────────────────────

(let [out-dir (io/file "Generated_Draíocht")]
  (.mkdirs out-dir)
  (doseq [group (:groups data)]
    (let [fname  (file-name (:group_id group))
          fpath  (io/file out-dir fname)
          content (generate-group-file group)]
      (spit fpath content)
      (println (str "  " fpath " (" (count (:entries group)) " entries)")))))

(println "\nDone. Files in Generated_Draíocht/")
