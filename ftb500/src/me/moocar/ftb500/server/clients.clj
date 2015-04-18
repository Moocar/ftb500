(ns me.moocar.ftb500.server.clients
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

(defrecord Clients [clients-atom])

(defn new-clients []
  (map->Clients {:clients-atom (atom {})}))

(defn get-or-make-client
  [clients game-id]
  (if (get clients game-id)
    clients
    (let [ch (async/chan 1)
          mult (async/mult ch)]
      (assoc clients game-id {:ch ch
                              :mult mult}))))

(defn get-client
  [clients game-id]
  (get (swap! (:clients-atom clients) get-or-make-client game-id)
       game-id))

(defn add-client
  [clients game-id send-ch]
  (let [client (get-client clients game-id)
        {:keys [mult]} client]
    (async/tap mult send-ch)
    nil))

(defn get-game-ch
  [clients game-id]
  (:ch (get (deref (:clients-atom clients)) game-id)))
