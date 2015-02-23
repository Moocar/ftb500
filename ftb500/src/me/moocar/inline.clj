(ns me.moocar.inline
  (:require [clojure.core.async :as async :refer [>! <! go-loop]]
            [com.stuartsierra.component :as component]))

(defrecord InlineServer [recv-ch])

(defn new-server [recv-ch]
  (map->InlineServer {:recv-ch recv-ch}))

(defrecord InlineClient [server recv-ch]
  component/Lifecycle
  (start [this]
    (let [send-ch (async/chan 1)]
      (go-loop []
        (when-let [msg (<! send-ch)]
          (>! (:recv-ch server)
              {:msg msg
               :send-ch recv-ch})
          (recur)))
      (assoc this
             :conn {:send-ch send-ch
                    :recv-ch recv-ch})))
  (stop [this]))

(defn new-client [server recv-ch]
  (map->InlineClient {:recv-ch recv-ch
                      :server server}))
