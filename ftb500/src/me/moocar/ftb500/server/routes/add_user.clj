(ns me.moocar.ftb500.server.routes.add-user
  (:require [clojure.core.async :as async :refer [<! >! go-loop]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))
{:msg
 {:route :user/add,
  :keys
  {:user/name "9a6239b4-d80e-4f68-8f5b-000f32fc06ae",
   :user/id #uuid "6fd7b4ff-1767-4a4b-8824-19e2bad9c710"}}}

(defn start-read-loop [this ch]
  (go-loop []
    (when-let [request (<! ch)]
      (let [{:keys [msg send-ch session-id]} request
            {user-id :user/id user-name :user/name} (:keys msg)
            {:keys [conn]} (:datomic-conn this)
            tx [{:db/id #db/id[:db.part/session]
                 :session.user/id user-id
                 :session.user/name user-name}]]
        @(d/transact conn tx)

        ;; Success
        (>! (:send-ch request) (:msg request)))
      (recur))))

(defrecord AddUser [;; Dependencies
                    server log-ch datomic-conn
                    ;; After started
                    ch]
  component/Lifecycle
  (start [this]
    (let [{:keys [route-pub-ch]} server
          ch (async/chan 1)]
      (async/sub route-pub-ch :user/add ch)
      (start-read-loop this ch)
      (assoc this :ch ch)))
  (stop [this]
    (let [{:keys [route-pub-ch]} server]
      (async/unsub route-pub-ch :game/add ch)
      (assoc this :ch nil))))

(defn construct []
  (component/using (map->AddUser {})
    {:server :me.moocar.ftb500/server
     :log-ch :log-ch
     :datomic-conn :datomic-conn}))
