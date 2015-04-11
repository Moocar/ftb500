(ns me.moocar.ftb500.client
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.lang :refer [uuid]]
            [me.moocar.remote :refer [idempotent-wait-for-confirmation]]))

(defn msg-topic-fn
  [msg]
  (prn "message topic" (select-keys (:msg msg) [:route :keys]))
  (select-keys (:msg msg) [:route :keys]))

(defrecord PubClient [server-chans ;dependencies
                      pub-ch ;after start
                      ]
  component/Lifecycle
  (start [this]
    (if pub-ch
      this
      (let [pub-ch (async/pub (:recv-ch server-chans) msg-topic-fn)]
        (assoc this :pub-ch pub-ch))))
  (stop [this]
    (if pub-ch
      (do (async/unsub-all pub-ch)
          (assoc this :pub-ch nil))
      this)))

(defn new-pub-client [config server-chans]
  (map->PubClient {:server-chans server-chans}))

(defn add-game-request [game-id]
  {:route :game/add
   :keys {:game/id game-id}})

(defn add-game
  [client]
  (let [request (add-game-request (uuid))]
    (idempotent-wait-for-confirmation client request)))
