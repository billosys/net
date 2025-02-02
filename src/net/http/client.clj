(ns net.http.client
  "Small wrapper around netty for HTTP clients."
  (:require [net.codec.b64      :as b64]
            [net.ssl            :as ssl]
            [net.http           :as http]
            [clojure.core.async :as async])
  (:import io.netty.bootstrap.Bootstrap
           io.netty.buffer.ByteBuf
           io.netty.channel.Channel
           io.netty.channel.ChannelHandlerContext
           io.netty.channel.ChannelHandlerAdapter
           io.netty.channel.ChannelInboundHandlerAdapter
           io.netty.channel.ChannelOutboundHandlerAdapter
           io.netty.channel.ChannelHandler
           io.netty.channel.ChannelOption
           io.netty.channel.ChannelInitializer
           io.netty.channel.ChannelFutureListener

           io.netty.channel.nio.NioEventLoopGroup
           io.netty.channel.socket.nio.NioSocketChannel
           io.netty.channel.epoll.Epoll
           io.netty.channel.epoll.EpollSocketChannel
           io.netty.channel.epoll.EpollEventLoopGroup

           io.netty.handler.logging.LoggingHandler
           io.netty.handler.logging.LogLevel
           io.netty.handler.codec.http.HttpClientCodec
           io.netty.handler.codec.http.HttpRequest
           io.netty.handler.codec.http.DefaultFullHttpRequest
           io.netty.handler.codec.http.HttpMethod
           io.netty.handler.codec.http.HttpHeaders
           io.netty.handler.codec.http.HttpResponseStatus
           io.netty.handler.codec.http.HttpVersion
           io.netty.handler.codec.http.HttpObjectAggregator
           io.netty.handler.codec.http.FullHttpResponse
           io.netty.handler.codec.http.DefaultFullHttpRequest
           io.netty.handler.codec.http.QueryStringEncoder
           io.netty.handler.ssl.SslContext
           io.netty.handler.ssl.SslHandler
           java.net.URI
           java.nio.charset.Charset
           javax.xml.bind.DatatypeConverter))

(defn response-handler
  "Capture context and msg and yield a closure
   which generates a response.

   The closure may be called at once or submitted to a pool."
  [f ^ChannelHandlerContext ctx ^FullHttpResponse msg]
  (fn []
    (try
      (let [headers (http/headers (.headers msg))
            body    (http/bb->string (.content msg))
            req     {:status         (some-> msg .getStatus .code)
                     :headers        headers
                     :version        (-> msg .getProtocolVersion .text)
                     :body           body}]
        (f req))
      (finally
        ;; This actually releases the content
        (.release msg)))))

(defn ^ChannelInboundHandlerAdapter netty-handler
  "Simple netty-handler, everything may happen in
   channel read, since we're expecting a full http request."
  [f]
  (proxy [ChannelInboundHandlerAdapter] []
    (exceptionCaught [^ChannelHandlerContext ctx e]
      (f {:status 5555 :error e}))
    (channelRead [^ChannelHandlerContext ctx ^FullHttpResponse msg]
      (let [callback (response-handler f ctx msg)]
        (callback)))))

(defn ssl-ctx-handler
  "Add an SSL context handler to a channel"
  [ssl-ctx ^Channel channel]
  (.newHandler ^SslContext ssl-ctx (.alloc channel)))

(defn request-initializer
  "Our channel initializer."
  ([ssl-ctx handler]
   (request-initializer ssl-ctx handler 1048576))
  ([ssl-ctx handler max-body-size]
   (proxy [ChannelInitializer] []
     (initChannel [^Channel channel]
       (let [pipeline (.pipeline channel)]
         (when ssl-ctx
           (.addLast pipeline "ssl" ^SslHandler (ssl-ctx-handler ssl-ctx channel)))
         (.addLast pipeline "codec"      (HttpClientCodec.))
         (.addLast pipeline "aggregator" (HttpObjectAggregator. (int max-body-size)))
         (.addLast pipeline "handler"    (netty-handler handler)))))))

(def http-versions
  "Keyword HTTP versions"
  {:http-1-1 HttpVersion/HTTP_1_1
   :http-1-0 HttpVersion/HTTP_1_0})

(defn string->version
  "When possible, get appropriate HttpVersion from a string.
   Throws when impossible."
  [^String s ^Exception e]
  (cond
    (re-matches #"(?i)http/1.1" s) HttpVersion/HTTP_1_1
    (re-matches #"(?i)http/1.0" s) HttpVersion/HTTP_1_0
    :else                          (throw e)))

(defn data->version
  "Get appropriate HttpVersion from a number of possible inputs.
   Defaults to HTTP/1.1 when nil, translate known keywords and
   strings or pass an HttpVersion through.

   Throws on other values."
  [v]
  (let [e (IllegalArgumentException. (str "invalid http version: " v))]
    (cond
      (nil? v)                  HttpVersion/HTTP_1_1
      (keyword? v)              (or (get http-versions v) (throw e))
      (string? v)               (string->version v e)
      (instance? HttpVersion v) v
      :else                     (throw e))))

(def http-methods
  "Keyword HTTP methods"
  {:connect HttpMethod/CONNECT
   :delete  HttpMethod/DELETE
   :get     HttpMethod/GET
   :head    HttpMethod/HEAD
   :options HttpMethod/OPTIONS
   :patch   HttpMethod/PATCH
   :post    HttpMethod/POST
   :put     HttpMethod/PUT
   :trace   HttpMethod/TRACE})

(defn data->method
  "Get appropriate HttpMethod from a number of possible inputs.
   Defaults to GET when nil, translate known keywords and strings,
   or pass a HttpMethod through.

   Throws on other values."
  [m]
  (let [e  (IllegalArgumentException. (str "invalid http method: " m))
        kw (when (string? m) (-> ^String m .toLowerCase keyword))]
    (cond
      (instance? HttpMethod m) m
      (nil? m)                 HttpMethod/GET
      (keyword? m)             (or (get http-methods m) (throw e))
      (string? m)              (or (get http-methods kw) (throw e))
      :else                    (throw e))))

(defn ^HttpHeaders data->headers
  [^HttpHeaders headers input ^String host]
  (when-not (or (empty? input) (map? input))
    (throw (ex-info "HTTP headers should be supplied as a map" {})))
  (.set headers "host" host)
  (when-not (or (get headers :connection)
                (get headers "connection"))
    (.set headers "connection" "close"))
  (doseq [[k v] input]
    (.set headers (name k) (str v)))
  headers)

(defn auth->headers
  "If basic auth is present as a map within a request, add the
   corresponding header."
  [^HttpHeaders headers {:keys [user password]}]
  (when (and user password)
    (.set headers "Authorization"
          (format "Basic %s" (b64/s->b64 (str user ":" password)))))
  headers)

(defn data->body
  "Add body to a request"
  [^DefaultFullHttpRequest request
   method
   ^String body]
  (when body
    (let [^HttpHeaders headers (.headers request)
          ^"[B" bytes   (cond
                          (string? body) (.getBytes ^String body)
                          :else          (throw (ex-info "wrong body type" {})))]
      (let [^ByteBuf content (.content request)]
        (-> content .clear (.writeBytes bytes)))
      (when-not (.get headers "Content-Length")
        (.set headers "Content-Length" (str (count body)))))))

(defn ^URI data->uri
  "Produce a valid URI from arguments found in a request map"
  [^URI uri query]
  (if (seq query)
    (let [qse (QueryStringEncoder. (str uri))]
      (doseq [[k v] query]
        (if (sequential? v)
          (doseq [subv v]
            (.addParam qse (name k) (str subv)))
          (.addParam qse (name k) (str v))))
      (.toUri qse))
    uri))

(defn data->request
  "Produce a valid request from a "
  [{:keys [body headers request-method version query uri auth]}]
  (let [uri     (data->uri uri query)
        version (data->version version)
        method  (data->method request-method)
        path    (str (.getRawPath uri) "?" (.getRawQuery uri))
        request (DefaultFullHttpRequest. version method path)]
    (data->headers (.headers request) headers (.getHost uri))
    (when auth
      (auth->headers (.headers request) auth))
    (data->body request method body)
    request))

(defn build-client
  "Create an http client instance. In most cases you will need only
   one per JVM. You may need several if you want to operate under
   different TLS contexts"
  ([]
   (build-client {}))
  ([{:keys [ssl] :as options}]
   (let [disable-epoll? (-> options :disable-epoll boolean)]
     {:channel (http/optimal-client-channel disable-epoll?)
      :group   (http/make-boss-group options)
      :ssl-ctx (ssl/client-context ssl)})))

(defn async-request
  "Execute an asynchronous HTTP request, produce the response
   asynchronously on the provided `handler` function.

   If no client is provided, create one."
  ([request-map handler]
   (async-request (build-client {}) request-map handler))
  ([{:keys [group channel ssl-ctx max-size]} request-map handler]
   (when-not (:uri request-map)
     (throw (ex-info "malformed request-map, needs :uri key" {})))
   (let [uri    (URI. (:uri request-map))
         scheme (if-let [scheme (.getScheme uri)] (.toLowerCase scheme) "http")
         host   (.getHost uri)
         port   (cond (not= -1 (.getPort uri)) (.getPort uri)
                      (= "http" scheme) 80
                      (= "https" scheme) 443)
         ssl?   (= "https" scheme)
         bs     (doto (Bootstrap.)
                  (.group   group)
                  (.channel channel)
                  (.handler (request-initializer (when ssl? ssl-ctx)
                                                 handler
                                                 (or max-size (* 1024 1024 10)))))
         ch     (some-> bs (.connect host (int port)) .sync .channel)]
     (.writeAndFlush ch (data->request (assoc request-map :uri uri)))
     (-> ch .closeFuture .sync))))

(defn request
  "Execute a request against an asynchronous client. If no client exists, create one.
   Waits for the response and returns it."
  ([request-map]
   (request (build-client {}) request-map))
  ([client request-map]
   (let [p (promise)]
     (async-request client request-map (fn [resp] (deliver p resp)))
     (deref p))))

(defn request-chan
  "Execute a request against an asynchronous client and produce the response on
   a promise channel."
  ([client request-map]
   (let [ch (async/promise-chan)]
     (try
       (async-request request-map
                      (fn [response]
                        (if response
                          (async/put! ch response)
                          (async/close! ch))))
       (catch Throwable t
         (async/put! ch t)))
     ch))
  ([request-map]
   (request-chan (build-client {}) request-map)))

(defn chunked?
  "Predicate to determine whether a request is chunked?"
  [request]
  (instance? clojure.core.async.impl.protocols.Channel (:body request)))
