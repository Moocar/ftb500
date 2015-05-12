(ns me.moocar.ftb500.client.system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]))

(defn construct [config]
  (let [tag (keyword (str "CLI" (+ 1000 (rand-int 1000))))]
    (component/system-map
     :transport-chans {:send-ch (async/chan 1)
                       :recv-ch (async/chan 1)}
     :me.moocar.ftb500/client (client/new-pub-client config)
     :log-ch (async/chan (async/dropping-buffer 1000)
                         (map #(assoc % :system tag))))))
