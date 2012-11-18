(ns leiningen.tasks
  (:use leiningen.lein-haml-sass.render-engine
        leiningen.lein-haml-sass.options
        leiningen.lein-common.lein-utils))

;; These macros are anaphoric in purpose to be bound to the lein tasks
;; defined in haml.clj, sass.clj, and scss.clj

(defmacro def-once [src-type]
  (let [type (name src-type)
        doc  (str "Compiles the " type " files once")]
    ;; ~'once makes the symbol once unqualified
    `(defn- ~'once
       ~doc
       [options#]
       (println "Compiling" ~type "files located in" (:src options#))
       (render-all! options# false true))))

(defmacro def-auto [src-type]
  (let [type (name src-type)]
    `(defn- ~'auto
       "Automatically recompiles when files are modified"
       [options#]
       (println "Ready to compile" ~type "files located in" (:src options#))
       (flush)
       (render-all! options# true))))

(defmacro def-clean [src-type]
  (let [type (name src-type)]
    `(defn- ~'clean
       "Removes the autogenerated files"
       [options#]
       (println "Deleting files generated by lein" ~type)
       (clean-all! options#))))

(defmacro def-deps [gem-name]
  (let [doc  (str "Installs the required " gem-name " gem")]
    `(defn- ~'deps
       ~doc
       [options#]
       (install-gem! options#))))

(defmacro def-lein-task [src-type]
  (let [type  (name src-type)
        fname (symbol type)
        doc   (str "Compiles " type " files.")]
    `(defn ~fname
       ~doc
       {:help-arglists '([~'once ~'auto ~'clean])
        :subtasks [~'#'once ~'#'auto ~'#'clean ~'#'deps]}
        ;; ~'#'blah defines an unqualified var named blah
       ([~'project]
          (exit-failure (lhelp/help-for ~type)))

       ([~'project ~'subtask & ~'args]
          (if-let [options# (extract-options ~src-type ~'project)]
            (case ~'subtask
              "once"  (~'once  options#)
              "auto"  (~'auto  options#)
              "clean" (~'clean options#)
              "deps"  (~'deps  options#)
              (task-not-found ~'subtask))
            (exit-failure))))))

;; Hooks stuffs
(defmacro def-hook [src-type task subtask args]
  `(let [options# (extract-options ~src-type (first ~args))]
     (apply ~task ~args)
     (when-not (~subtask (:ignore-hooks options#))
       (~(symbol (name subtask)) options#))))
