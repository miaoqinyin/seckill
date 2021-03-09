package cn.wolfcode.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;


import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.concurrent.ConcurrentHashMap;

@Setter
@Getter
@ServerEndpoint("/{uuid}")
@Component
public class WebSocketServer {
    private Session session;
    public static ConcurrentHashMap<String,WebSocketServer> clients = new ConcurrentHashMap<>();
    @OnOpen
    public void onOpen(Session session, @PathParam( "uuid") String uuid){
        System.out.println("客户端连接===>"+uuid);
        this.session = session;
        clients.put(uuid,this);
    }
    @OnClose
    public void onClose(@PathParam( "uuid") String uuid){
        clients.remove(uuid);
    }
    @OnError
    public void onError(Throwable error) {
        error.printStackTrace();
    }
}