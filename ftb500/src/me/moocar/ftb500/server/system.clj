(ns me.moocar.ftb500.server.system
  (:require [clojure.core.async :as async]
            [me.moocar.ftb500.server.routes.system :as route-system]))

(defn new-system
  [config recv-ch]
  (let [route-pub-ch (async/pub recv-ch #(do (println "got route!" %) ((comp :route :msg) %)))]
    (merge
     (route-system/new-system)
     {:route-pub-ch route-pub-ch})))
