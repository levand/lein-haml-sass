(ns leiningen.lein-haml-sass.render-engine
  (:use leiningen.lein-common.file-utils)
  (:require [clojure.java.io :as io])
  (:import [org.jruby.embed ScriptingContainer LocalContextScope]
           [org.jruby RubyHash RubySymbol]))

(def ^:private c (ref nil))
(def ^:private runtime (ref nil))

(def ^:private haml-engine (ref nil))
(def ^:private sass-engine (ref nil))

(def ^:private empty-options (ref nil))
(def ^:private sass-options (ref nil))
(def ^:private scss-options (ref nil))

(defn- rb-options-for [src-type]
  (let [rb-hash (RubyHash. @runtime)]
    (.put rb-hash
          (RubySymbol/newSymbol @runtime "syntax")
          (RubySymbol/newSymbol @runtime (name src-type)))
    rb-hash))

(defn- ensure-engine-started! []
  (when-not @c
    (dosync
     (ref-set c (ScriptingContainer. LocalContextScope/THREADSAFE))

     (def gempath ["gems/gems/haml-3.1.7/lib", "src/gems/gems/sass-3.2.1/lib"])
     (.setLoadPaths @c gempath)
     (.runScriptlet @c "require 'rubygems'; require 'haml'; require 'sass'")
     (ref-set haml-engine (.runScriptlet @c "Haml::Engine"))
     (ref-set sass-engine (.runScriptlet @c "Sass::Engine"))
     (ref-set runtime (-> (.getProvider @c) .getRuntime))
     (ref-set empty-options (RubyHash. @runtime))
     (ref-set sass-options  (rb-options-for :sass))
     (ref-set scss-options  (rb-options-for :scss)))))

(defn- engine-options-for [src-type]
  (case src-type
    :sass @sass-options
    :scss @scss-options
    @empty-options))

(defn- engine-for [src-type]
  (case src-type
    :haml haml-engine
    :sass sass-engine
    :scss sass-engine))

(defn- files-from
  [{:keys [src src-type output-directory output-extension]}]
  (dest-files-from (name src-type) src output-directory output-extension))

(defn render [src-type template]
  (let [args          (to-array [template (engine-options-for src-type)])
        engine-class (engine-for src-type)
        engine        (.callMethod @c @engine-class "new" args Object)]
    (.callMethod @c engine "render" String)))

(defn render-all! [{:keys [src-type auto-compile-delay] :as options} watch?]
  (ensure-engine-started!)
  (loop []
    (doseq [file-descriptor (files-from options)]
      (let [dest-file (io/file (:dest file-descriptor))
            src-file (io/file (src-type file-descriptor))]
        (when (or (not (.exists dest-file))
                  (> (.lastModified src-file) (.lastModified dest-file)))
          (io/make-parents dest-file)
          (spit dest-file (render src-type (slurp (src-type file-descriptor))))
          (println (str "   [" (name src-type) "] - " (java.util.Date.) " - " src-file " -> " dest-file)))))

    (when watch?
      (Thread/sleep auto-compile-delay)
      (recur))))

(defn clean-all! [{:keys [output-directory delete-output-dir] :as options}]
  (doseq [file-descriptor (files-from options)]
    (delete-file! (io/file (:dest file-descriptor))))

  (when (and delete-output-dir (exists output-directory) (dir-empty? output-directory))
    (println (str "Destination folder " output-directory " is empty - Deleting it"))
    (delete-directory-recursively! output-directory)))