(ns me.moocar.websocket
  (:require [clojure.core.async :as async :refer [go <! >! alt!]]
            [clojure.java.io :as jio]
            [cognitect.transit :as transit]
            me.moocar.byte-buffer)
  (:import (java.nio ByteBuffer)
           (org.eclipse.jetty.websocket.api WebSocketListener Session
                                            WriteCallback RemoteEndpoint)))

(defn clj->transit-buf
  "Serializes a clojure datastructure into a byte buffer using
  transit :json format. buffer is rewound before being returned"
  [msg]
  (let [buf (ByteBuffer/allocate 4096)
        output-stream (jio/output-stream buf)
        writer (transit/writer output-stream :json)]
    (transit/write writer msg)
    (.rewind buf)
    buf))

(defn transit-buf->clj
  "Deserializes a ByteBuffer into a clojure data structure. Assumes
  buffer is encoded using :json transit"
  [buf]
  (let [input-stream (jio/input-stream buf)
        reader (transit/reader input-stream :json)]
    (transit/read reader)))

(defn write
  "Writes a clojure data structure to a session's remote using
  transit :json"
  [session error-ch msg]
  (try
    (.sendBytes (.getRemote session) (clj->transit-buf msg))
    (catch Throwable t
      (println t)
      (async/put! error-ch t))))

(defn conn-loop
  "Waits for a connection to occur, and then loops waiting for IO.
  Incoming messages to the server are consumed from the connection's
  read-ch. They are converted into a clojure data structure and put
  onto recv-ch. Outgoing messages are consumed from the connection's
  send-ch, converted to a byte buffer, and sent using the session's
  remote. The loop finishes once a disconnect occurs"
  [transport-chans conn]
  (let [{:keys [error-ch connect-ch read-ch log-ch]} conn
        {:keys [send-ch recv-ch]} transport-chans]
    (go
      (when-let [session (<! connect-ch)]
        (loop []
          (alt!

            read-ch
            ([buf]
             #_(>! log-ch {:read buf})
             (when buf
               (try
                 (let [msg-wrapper {:msg (transit-buf->clj buf)
                                    :send-ch send-ch}]
                   #_(>! log-ch {:msg-wrapper msg-wrapper})
                   (>! recv-ch msg-wrapper))
                 (catch Throwable t
                   (>! log-ch {:error t})
                   (async/put! error-ch t)))
               (recur)))

            send-ch
            ([msg]
             (when msg
               #_(>! log-ch {:send! msg})
               (write session error-ch msg)
               (recur)))

            connect-ch
            ([[status-code reason]]
             (>! log-ch {:connect! [status-code reason]})
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
      (when cause
        (async/put! error-ch cause)))
    (onWebSocketClose [this status-code reason]
      (async/put! connect-ch [status-code reason]))))

(defn default-conn-f
  "Creates a default connection map"
  []
  {:read-ch (async/chan 1)
   :connect-ch (async/chan 1)
   :error-ch (async/chan 1)})
