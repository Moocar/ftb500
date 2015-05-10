(ns me.moocar.client-test
  (:require [clojure.core.async :as async :refer [go >!! <!! <! >! go-loop]]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]
            [me.moocar.lang :refer [uuid deep-merge]]
            [me.moocar.ftb500.server :as server]
            [me.moocar.websocket.full-system :as full-system]))

(defn log-loop
  [components]
  (let [log-chans (map :log-ch components)]
    (go-loop []
      (let [[v p] (async/alts! log-chans)]
        (prn (:system v) (dissoc v :system))
        (recur)))))

(deftest t-all
  (let [config {:port 8080
                :hostname "localhost"}
        server (component/start (full-system/new-server-system config))
        log-ch (async/chan 1 (map #(assoc % :system :TEST001)))]
    (try
      (let [client-system (component/start (full-system/new-client-system config))]
        (try
          (log-loop [client-system server {:log-ch log-ch}])
          (server/log-tap (:server server))
          (let [{:keys [client]} client-system
                game-id (uuid)
                response-ch (async/chan 1)]
            (client/add-game client game-id response-ch)
            (>!! log-ch {:response (<!! response-ch)}))
          (finally
            (component/stop client-system))))
      (finally
        (component/stop server)))))

(defn test-config
  []
  {:server {:datomic {;; Generate a new db-name each time due to the
                      ;; tranactor needing 1-minute to delete
                      ;; database:
                      ;; https://groups.google.com/forum/#!msg/datomic/1WBgM84nKmc/UzhyugWk6loJ
                      :db-name (str (uuid))
                      :reset-db? true
                      :create-database? true
                      :create-schema? true}}})

(defn load-test-config []
  (deep-merge (edn/read-string (slurp "config.edn"))
              (test-config)))

(deftest t-joined
  (let [config (load-test-config)
        server (component/start (full-system/new-server-system config))]
    (try
      (let [client-systems (repeatedly 2 #(component/start (full-system/new-client-system config)))
            log-ch (async/chan 1 (map #(assoc % :system :TEST001)))]
        (try
          (log-loop (conj (conj client-systems {:log-ch log-ch})
                          server))
          (server/log-tap (:server server))
          (let [[client1 client2] (map :client client-systems)
                game-id (uuid)
                response-ch (async/chan 1)]
            (client/log-tap client1)
            (client/add-game client1 game-id response-ch)
            (<!! response-ch)
            (client/get-game client1 game-id response-ch)
            (let [game-info (<!! response-ch)
                  seats (get-in game-info [:msg :body :seats])]
              (client/join-game client1 game-id (first seats) response-ch)
              (>!! log-ch {"joined?" (<!! response-ch)})
              (client/join-game client2 game-id (second seats) response-ch)
              (>!! log-ch {"joined?" (<!! response-ch)})))
          (finally
            (doseq [client-system client-systems]
              (component/stop client-system)))))
      (finally
        (component/stop server)))))
