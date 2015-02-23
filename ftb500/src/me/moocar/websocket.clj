(ns me.moocar.websocket
  (:require [clojure.core.async :as async :refer [go <! >! alt!]]
            [clojure.java.io :as jio]
            [cognitect.transit :as transit]
            me.moocar.byte-buffer)
  (:import (java.nio ByteBuffer)
           (org.eclipse.jetty.websocket.api WebSocketListener Session
                                            WriteCallback RemoteEndpoint)))

(defn clj->transit-buf [msg]
  (let [buf (ByteBuffer/allocate 4096)
        output-stream (jio/output-stream buf)
        writer (transit/writer output-stream :json)]
    (transit/write writer msg)
    (.rewind buf)
    buf))

(defn transit-buf->clj [buf]
  (let [input-stream (jio/input-stream buf)
        reader (transit/reader input-stream :json)]
    (transit/read reader)))

(defn write
  [session error-ch msg]
  (try
    (.sendBytes (.getRemote session) (clj->transit-buf msg))
    (catch Throwable t
      (println t)
      (async/put! error-ch t))))

(defn conn-loop
  [recv-ch {:keys [error-ch connect-ch read-ch send-ch write-ch] :as conn}]
  (let []
    (go
      (when-let [session (<! connect-ch)]
        (loop []
          (alt!

            read-ch
            ([buf]
             (when buf
               (try
                 (let [msg-wrapper {:msg (transit-buf->clj buf)
                                    :send-ch send-ch}]
                   (>! recv-ch msg-wrapper))
                 (catch Throwable t
                   (println "throwable " t)
                   (async/put! error-ch t)))
               (recur)))

            send-ch
            ([msg]
             (when msg
               (println "send!" msg)
               (write session error-ch msg)
               (recur)))

            connect-ch
            ([[status-code reason]]
             (println "connect msg" status-code reason)
             (async/close! connect-ch))))))))

(defn listener
  "Returns a websocket listener that does nothing but put connections,
  reads or errors into the respective channels"
  [{:keys [connect-ch read-ch error-ch] :as conn}]
  (reify WebSocketListener
    (onWebSocketConnect [this session]
      (async/put! connect-ch session))
    (onWebSocketText [this message]
      (throw (UnsupportedOperationException. "Text not supported")))
    (onWebSocketBinary [this bytes offset len]
      (async/put! read-ch (ByteBuffer/wrap bytes offset len)))
    (onWebSocketError [this cause]
      (when-not error-ch
        (println "not error ch!!!"))
      (when cause
        (async/put! error-ch cause)))
    (onWebSocketClose [this status-code reason]
      (async/put! connect-ch [status-code reason]))))

(defn default-conn-f
  "Creates a default connection map"
  []
  {:read-ch (async/chan 1)
   :write-ch (async/chan 1)
   :connect-ch (async/chan 1)
   :error-ch (async/chan 1)
   :send-ch (async/chan 1)})
