(ns me.moocar.inline
  (:require [clojure.core.async :as async :refer [>! <! go-loop]]
            [com.stuartsierra.component :as component]))

(defrecord InlineServer [recv-ch])

(defn new-server [recv-ch]
  (map->InlineServer {:recv-ch recv-ch}))

(defrecord InlineClient [server transport-chans]
  component/Lifecycle
  (start [this]
    (let [{:keys [send-ch recv-ch]} transport-chans
          internal-recv-ch (async/chan 1)]
      (go-loop []
        (when-let [msg (<! internal-recv-ch)]
          (>! recv-ch {:msg msg
                       :send-ch send-ch})))
      (go-loop []
        (when-let [msg (<! send-ch)]
          (>! (:recv-ch server)
              {:msg msg
               :send-ch internal-recv-ch})
          (recur)))
      this))
  (stop [this]
    this))

(defn new-client
  "Creates a new inline client. The client knows about the server. "
  [server]
  (component/using
    (map->InlineClient {:server server})
    [:transport-chans]))
