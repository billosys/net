(ns net.http
  "Functions common to HTTP clients and servers"
  (:import io.netty.channel.ChannelHandlerContext
           io.netty.channel.ChannelHandlerAdapter
           io.netty.channel.ChannelInboundHandlerAdapter
           io.netty.channel.ChannelOutboundHandlerAdapter
           io.netty.channel.ChannelHandler
           io.netty.channel.ChannelOption
           io.netty.channel.ChannelInitializer
           io.netty.channel.ChannelFutureListener
           io.netty.channel.EventLoopGroup
           io.netty.channel.nio.NioEventLoopGroup
           io.netty.channel.socket.SocketChannel
           io.netty.channel.socket.nio.NioServerSocketChannel
           io.netty.channel.socket.nio.NioSocketChannel
           io.netty.channel.epoll.Epoll
           io.netty.channel.epoll.EpollServerSocketChannel
           io.netty.channel.epoll.EpollSocketChannel
           io.netty.channel.epoll.EpollEventLoopGroup
           io.netty.handler.logging.LoggingHandler
           io.netty.handler.logging.LogLevel
           io.netty.handler.codec.http.FullHttpRequest
           io.netty.handler.codec.http.HttpServerCodec
           io.netty.handler.codec.http.HttpMethod
           io.netty.handler.codec.http.HttpHeaders
           io.netty.handler.codec.http.HttpResponseStatus
           io.netty.handler.codec.http.DefaultHttpResponse
           io.netty.handler.codec.http.DefaultHttpContent
           io.netty.handler.codec.http.DefaultLastHttpContent
           io.netty.handler.codec.http.HttpRequest
           io.netty.handler.codec.http.HttpContent
           io.netty.handler.codec.http.LastHttpContent
           io.netty.handler.codec.http.HttpVersion
           io.netty.handler.codec.http.HttpObjectAggregator
           io.netty.handler.codec.http.QueryStringDecoder
           io.netty.bootstrap.AbstractBootstrap
           io.netty.bootstrap.ServerBootstrap
           io.netty.buffer.Unpooled
           io.netty.buffer.ByteBuf
           io.netty.buffer.ByteBufAllocator
           io.netty.buffer.UnpooledByteBufAllocator
           java.io.InputStream
           java.io.File
           java.io.FileInputStream
           java.nio.charset.Charset
           java.nio.ByteBuffer))

(defn epoll?
  "Find out if epoll is available on the underlying platform."
  []
  (Epoll/isAvailable))

(defn ^String bb->string  "Convert a ByteBuf to a UTF-8 String."
  [^ByteBuf bb]
  (.toString bb (Charset/forName "UTF-8")))

(def method->data
  "Yield a keyword representing an HTTP method."
  {HttpMethod/CONNECT :connect
   HttpMethod/DELETE  :delete
   HttpMethod/GET     :get
   HttpMethod/HEAD    :head
   HttpMethod/OPTIONS :options
   HttpMethod/PATCH   :patch
   HttpMethod/POST    :post
   HttpMethod/PUT     :put
   HttpMethod/TRACE   :trace})

(def log-levels
  "Keyword to level map used as a helper when
   setting up log handlers."
  {:debug LogLevel/DEBUG
   :info  LogLevel/INFO
   :warn  LogLevel/WARN})

(defn headers
  "Get a map out of netty headers."
  [^HttpHeaders headers]
  (into
   {}
   (map (fn [[^String k ^String v]] [(-> k .toLowerCase keyword) v]))
   (.entries headers)))

(defn make-boss-group
  "Create an event loop group. Try setting up an epoll event loop group
   unless either instructed not to do so or it is no available."
  [{:keys [loop-thread-count disable-epoll]}]
  (if (and (epoll?) (not disable-epoll))
    (EpollEventLoopGroup. (int (or loop-thread-count 1)))
    (NioEventLoopGroup. (int (or loop-thread-count 1)))))

(defn set-log-handler!
  "Add log hander to a bootstrap"
  [^AbstractBootstrap bootstrap {:keys [logging]}]
  (let [handler (when-let [level (some-> logging keyword (get log-levels))]
                  (LoggingHandler. ^LogLevel level))]
    (cond-> bootstrap  handler (.handler handler))))

(defn set-optimal-server-channel!
  "Add optimal channel to a server bootstrap"
  [^AbstractBootstrap bs disable-epoll?]
  (.channel bs (if (and (not disable-epoll?) (epoll?))
                 EpollServerSocketChannel NioServerSocketChannel))
  bs)

(defn optimal-client-channel
  "Figure out which client channel to use"
  [disable-epoll?]
  (if (and (not disable-epoll?) (epoll?))
    EpollSocketChannel NioSocketChannel))

(def logging-re #"(?i)^(debug|info|warn)$")
