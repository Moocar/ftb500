(ns me.moocar.websocket-test
  (:require [clojure.core.async :as async :refer [go >!! <!! <! >! go-loop]]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [me.moocar.component :refer [with-test-system]]
            [me.moocar.websocket.system :as system]))

(defn local-config []
  {:server {:websocket {:hostname "localhost"
                        :port :random}}})

(defn update-config [config server-system]
  (assoc-in config [:server :websocket :port]
            (:local-port (:me.moocar.websocket/server server-system))))

(defmacro with-full-system [[binding-form config] & body]
  `(with-test-system [server-system# (system/new-server ~config)]
     (let [config# (update-config ~config server-system#)]
       (with-test-system [client-system# (system/new-client config#)]
         (let [~binding-form {:server-system server-system#
                              :client-system client-system#}]
           ~@body)))))

(deftest t-should-add-session-id
  (with-full-system [{:keys [server-system client-system]} (local-config)]
    (let [cookies (.getCookies (:cookie-store client-system))]
      (is (= 1 (count (filter #(= (.getName %) "JSESSIONID") cookies)))))))

(deftest t-echo-server
  (with-full-system [{:keys [server-system client-system]} (local-config)]
    (let [{:keys [client transport-chans]} client-system
          {:keys [server-recv-ch]} server-system
          msg {:route :moo/car :body "this is my body"}]
      (>!! (:send-ch transport-chans) msg)
      (go
        (when-let [packet (<! server-recv-ch)]
          (is (= :me.moocar.websocket/connect (:route (:request packet))))
          (when-let [packet (<! server-recv-ch)]
            (is (:session-id packet))
            (>! (:send-ch packet) (:request packet)))))
      (is (= msg (:request (<!! (:recv-ch transport-chans))))))))
