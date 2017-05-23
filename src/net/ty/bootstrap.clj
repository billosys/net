(ns net.ty.bootstrap
  "A clojure facade to build netty bootstraps.
   In netty, bootstraps are helpers to get channels."
  (:require [net.ty.channel :as chan])
  (:import java.net.InetAddress
           java.net.NetworkInterface
           java.net.SocketAddress
           io.netty.util.AttributeKey
           io.netty.bootstrap.AbstractBootstrap
           io.netty.bootstrap.ServerBootstrap
           io.netty.bootstrap.Bootstrap
           io.netty.buffer.ByteBufAllocator
           io.netty.channel.RecvByteBufAllocator
           io.netty.channel.MessageSizeEstimator
           io.netty.channel.ChannelOption
           io.netty.channel.ChannelHandler
           io.netty.channel.EventLoopGroup
           io.netty.channel.nio.NioEventLoopGroup
           io.netty.channel.socket.nio.NioServerSocketChannel
           io.netty.channel.socket.nio.NioSocketChannel
           io.netty.channel.socket.nio.NioDatagramChannel
           java.net.InetAddress))

(def channel-options
  "Valid options for bootstraps"
  {:allocator                    ChannelOption/ALLOCATOR
   :allow-half-closure           ChannelOption/ALLOW_HALF_CLOSURE
   :auto-read                    ChannelOption/AUTO_READ
   :connect-timeout-millis       ChannelOption/CONNECT_TIMEOUT_MILLIS
   :ip-multicast-addr            ChannelOption/IP_MULTICAST_ADDR
   :ip-multicast-if              ChannelOption/IP_MULTICAST_IF
   :ip-multicast-loop-disabled   ChannelOption/IP_MULTICAST_LOOP_DISABLED
   :ip-multicast-ttl             ChannelOption/IP_MULTICAST_TTL
   :ip-tos                       ChannelOption/IP_TOS
   :max-messages-per-read        ChannelOption/MAX_MESSAGES_PER_READ
   :message-size-estimator       ChannelOption/MESSAGE_SIZE_ESTIMATOR
   :rcvbuf-allocator             ChannelOption/RCVBUF_ALLOCATOR
   :so-backlog                   ChannelOption/SO_BACKLOG
   :so-broadcast                 ChannelOption/SO_BROADCAST
   :so-keepalive                 ChannelOption/SO_KEEPALIVE
   :so-linger                    ChannelOption/SO_LINGER
   :so-rcvbuf                    ChannelOption/SO_RCVBUF
   :so-reuseaddr                 ChannelOption/SO_REUSEADDR
   :so-sndbuf                    ChannelOption/SO_SNDBUF
   :so-timeout                   ChannelOption/SO_TIMEOUT
   :tcp-nodelay                  ChannelOption/TCP_NODELAY
   :write-buffer-high-water-mark ChannelOption/WRITE_BUFFER_HIGH_WATER_MARK
   :write-buffer-low-water-mark  ChannelOption/WRITE_BUFFER_LOW_WATER_MARK
   :write-spin-count             ChannelOption/WRITE_SPIN_COUNT})

(defn ^ChannelOption ->channel-option
  [^clojure.lang.Keyword k]
  (or (channel-options k)
      (throw (ex-info (str "invalid channel option: " (name k)) {}))))

(def nio-server-socket-channel NioServerSocketChannel)
(def nio-socket-channel        NioSocketChannel)
(def nio-datagram-channel      NioDatagramChannel)

(defn ^EventLoopGroup nio-event-loop-group
  "Yield a new NioEventLoopGroup"
  []
  (NioEventLoopGroup.))

(defn ^ServerBootstrap server-bootstrap
  "Build a server bootstrap from a configuration map"
  [config]
  (let [bs (ServerBootstrap.)
        group ^EventLoopGroup (:child-group config)]
    (if-let [c ^EventLoopGroup (:child-group config)]
      (.group bs (or group (nio-event-loop-group)) c)
      (.group bs (or group (nio-event-loop-group))))
    (.channel bs (or (:channel config) nio-server-socket-channel))
    (doseq [[k v] (:options config) :let [copt (->channel-option k)]]
      (.option bs copt (if (number? v) (int v) v)))
    (doseq [[k v] (:child-options config) :let [copt (->channel-option k)]]
      (.childOption bs copt (if (number? v) (int v) v)))
    (doseq [[k v] (:child-attrs config)]
      (.childAttr bs (AttributeKey/valueOf (name k)) v))
    (.childHandler bs (:handler config))
    (.validate bs)))

(defn remote-address!
  "Set remote address for a client bootstrap, allows host and port
   to be provided as a SocketAddress"
  ([^Bootstrap bs ^SocketAddress sa]
   (.remoteAddress bs sa))
  ([^Bootstrap bs
    host
    ^Long port]
   (cond
     (string? host)
     (.remoteAddress bs ^String host (int port))

     (instance? InetAddress host)
     (.remoteAddress bs ^InetAddress host (int port))

     :else
     (throw (IllegalArgumentException. "invalid address")))))

(defn ^AbstractBootstrap bootstrap
  "Build a client bootstrap from a configuration map"
  [config]
  (let [bs (Bootstrap.)]
    (.group bs (or (:group config) (nio-event-loop-group)))
    (.channel bs (or (:channel config) nio-socket-channel))
    (doseq [[k v] (:options config) :let [copt (->channel-option k)]]
      (.option bs copt (if (number? v) (int v) v)))
    (doseq [[k v] (:attrs config)]
      (.attr bs (AttributeKey/valueOf (name k)) v))
    (when-let [[host port] (:remote-address config)]
      (remote-address! bs host port))
    (.handler bs (:handler config))
    (.validate bs)))

(defn bind!
  "Bind bootstrap to a host and port"
  [^ServerBootstrap bs ^String host ^Long port]
  (.bind bs host (int port)))

(defn connect!
  "Attempt connection of a bootstrap. Accepts as pre-configured bootstrap,
   and optionally a SocketAddressor Host and Port."
  ([^Bootstrap bs]
   (.connect bs))
  ([^Bootstrap bs ^SocketAddress sa]
   (.connect bs sa))
  ([^Bootstrap bs ^InetAddress x y]
   (.connect bs x (int y))))

(defn local-address!
  "Sets the bootstrap's local address. Accepts either a SocketAddress or
   Host and Port."
  ([^AbstractBootstrap bs x]
   (.localAddress bs (int x)))
  ([^AbstractBootstrap bs ^InetAddress x y]
   (.localAddress bs x (int y))))

(defn validate!
  "Validate that a bootstrap has correct parameters."
  ([^AbstractBootstrap bs]
   (.validate bs)))

(defn set-group!
  "Set the group on top of which channels will be created and then handled."
  [^AbstractBootstrap bs group]
  (.group bs group))

(defn shutdown-gracefully!
  "Gracefully shut down a group"
  [^EventLoopGroup group]
  (.shutdownGracefully group))

(defn shutdown-fn
  "Closure to shutdown a channel and associated group"
  [chan group]
  (fn []
    (chan/close! chan)
    (shutdown-gracefully! group)))

(defn set-child-handler!
  "A server bootstrap has a child handler, this methods helps set it"
  [^ServerBootstrap bootstrap
   ^ChannelHandler handler]
  (.childHandler bootstrap handler))
