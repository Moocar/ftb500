(ns me.moocar.ftb500.server
  (:require [clojure.core.async :as async :refer [go-loop >! <!]]
            [com.stuartsierra.component :as component]))

(defrecord PubServer [;; Dependency
                      recv-ch log-ch
                      ;; After started
                      route-pub-ch pub-tap mult
                      ]
  component/Lifecycle
  (start [this]
    (if route-pub-ch
      this
      (let [mult (async/mult recv-ch)
            pub-tap (async/chan 1)
            route-pub-ch (async/pub pub-tap (comp :route :msg))]
        (println "tapping")
        (async/tap mult pub-tap)
        (assoc this
               :route-pub-ch route-pub-ch
               :pub-tap pub-tap
               :mult mult))))
  (stop [this]
    (if route-pub-ch
      (do (async/unsub-all route-pub-ch)
          (async/untap mult pub-tap)
          (assoc this :route-pub-ch nil :pub-tap nil :mult nil))
      this)))

(defn log-tap
  [server]
  (let [ch (async/chan 1)]
    (async/tap (:mult server) ch)
    (go-loop []
      (when-let [msg (:msg (<! ch))]
        (>! (:log-ch server) {:srv-recv msg})
        (recur)))))

(defn new-server
  [config]
  (component/using
    (map->PubServer {})
    {:log-ch :log-ch
     :recv-ch :server-recv-ch}))
