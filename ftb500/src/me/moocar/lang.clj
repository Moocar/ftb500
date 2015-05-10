(ns me.moocar.lang)

(defn deep-merge
  "Like merge, but merges maps recursively."
  [& maps]
  (if (every? (some-fn nil? map?) maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(defn uuid
  ([string]
   (java.util.UUID/fromString string))
  ([]
   (java.util.UUID/randomUUID)))

(defn uuid?
  [thing]
  (instance? java.util.UUID thing))
