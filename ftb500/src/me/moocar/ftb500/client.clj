(ns me.moocar.ftb500.client
  (:require [clojure.core.async :as async :refer [go-loop <! >!]]
            [com.stuartsierra.component :as component]
            [me.moocar.lang :refer [uuid]]
            [me.moocar.remote :refer [idempotent-get]]))

(defn msg-topic-fn
  [msg]
  (select-keys (:msg msg) [:route :keys]))

(defrecord PubClient [;; dependencies
                      server-chans log-ch
                      ;; after start
                      pub-ch pub-tap mult]
  component/Lifecycle
  (start [this]
    (if pub-ch
      this
      (let [mult (async/mult (:recv-ch server-chans))
            pub-tap (async/chan 1)
            pub-ch (async/pub pub-tap msg-topic-fn)]
        (async/tap mult pub-tap)
        (assoc this
               :pub-ch pub-ch
               :pub-tap pub-tap
               :mult mult))))
  (stop [this]
    (if pub-ch
      (do (async/unsub-all pub-ch)
          (async/untap mult pub-tap)
          (assoc this :pub-ch nil :pub-tap nil :mult nil))
      this)))

(defn log-tap
  [client]
  (let [ch (async/chan 1)]
    (async/tap (:mult client) ch)
    (go-loop []
      (when-let [msg (:msg (<! ch))]
        (>! (:log-ch client) {:cli-recv msg})
        (recur)))))

(defn new-pub-client [config]
  (component/using
    (map->PubClient {})
    {:server-chans :transport-chans
     :log-ch :log-ch}))

(defn add-game-request [game-id]
  {:route :game/add
   :keys {:game/id game-id}})

(defn add-game
  [client game-id ch]
  (let [request (add-game-request game-id)]
    (idempotent-get client request ch)))

(defn add-user
  [client player-name ch]
  (let [request {:route :user/add
                 :keys {:user/name player-name
                        :user/id (uuid)}}]
    (idempotent-get client request ch)))

(defn get-game
  [client game-id ch]
  (let [request {:route :game/get
                 :keys {:game/id game-id}}]
    (idempotent-get client request ch)))

(defn joined? [response]
  (not (:error response)))

(defn join-game
  [client game-id seat-id ch]
  (let [request {:route :game/join
                 :keys {:game/id game-id
                        :seat/id seat-id}}]
    (idempotent-get client request ch)))
