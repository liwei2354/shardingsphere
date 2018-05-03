package com.saaavsaaa.client.zookeeper;

import com.saaavsaaa.client.action.IClient;
import com.saaavsaaa.client.utility.PathUtil;
import com.saaavsaaa.client.utility.StringUtil;
import com.saaavsaaa.client.utility.constant.Constants;
import com.saaavsaaa.client.utility.section.Listener;
import com.saaavsaaa.client.utility.section.Properties;
import com.saaavsaaa.client.utility.section.WatcherCreator;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Created by aaa
 */
public abstract class Client implements IClient {
    private static final CountDownLatch CONNECTED = new CountDownLatch(1);
    protected static final Map<String, Watcher> watchers = new ConcurrentHashMap<>();
    
    protected final boolean watched = true; //false
    private final String servers;
    private final int sessionTimeOut;
    private String scheme;
    private byte[] auth;
    
    protected ZooKeeper zooKeeper;
    protected List<ACL> authorities;
    private boolean globalListenerRegistered = false;
    protected String rootNode = "/InitValue";
    protected boolean rootExist = false;
    private ClientFactory clientFactory;
    
    protected Client(final String servers, final int sessionTimeoutMilliseconds) {
        this.servers = servers;
        this.sessionTimeOut = sessionTimeoutMilliseconds;
    }
    
    public synchronized void start() throws IOException, InterruptedException {
        zooKeeper = new ZooKeeper(servers, sessionTimeOut, startWatcher());
        zooKeeper.addAuthInfo(scheme , auth);
        CONNECTED.await();
    }
    
    ZooKeeper getZooKeeper(){
        return zooKeeper;
    }
    
    private Watcher startWatcher() {
        return new Watcher(){
            public void process(WatchedEvent event) {
                if(Event.KeeperState.SyncConnected == event.getState()){
                    if(Event.EventType.None == event.getType()){
                        CONNECTED.countDown();
                    }
                }
                if (globalListenerRegistered){
                    watchers.get(Constants.GLOBAL_LISTENER_KEY).process(event);
                }
                if (Properties.INSTANCE.watchOn() && watchers.containsKey(event.getPath())){
                     watchers.get(event.getPath()).process(event);
                }
            }
        };
    }
    
    void registerWatch(final Listener globalListener){
        if (globalListenerRegistered){
            return;
        }
        watchers.put(Constants.GLOBAL_LISTENER_KEY, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                globalListener.process(event);
            }
        });
        globalListenerRegistered = true;
    }
    
    public Watcher registerWatch(final String key, final Listener listener){
        String path = PathUtil.getRealPath(rootNode, key);
//        listener.setKey(path);
        Watcher watcher = new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                listener.process(event);
            }
        };
        watchers.put(path, watcher);
        return watcher;
    }
    
    public void unregisterWatch(final String key){
        if (StringUtil.isNullOrBlank(key)){
            throw new IllegalArgumentException("key should not be blank");
        }
        String path = PathUtil.getRealPath(rootNode, key);
        if (watchers.containsKey(path)){
            watchers.remove(path);
        }
    }
    
    public void close() throws InterruptedException {
        zooKeeper.close();
    }
    
    void createNamespace() throws KeeperException, InterruptedException {
        createNamespace(Constants.NOTHING_DATA);
    }
    
   private void createNamespace(final byte[] date) throws KeeperException, InterruptedException {
        if (rootExist){
            return;
        }
        try {
            zooKeeper.create(rootNode, date, authorities, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException ee){
            System.out.println("root create : " + ee.getMessage());
            return;
        }
        rootExist = true;
        zooKeeper.exists(rootNode, WatcherCreator.deleteWatcher(rootNode, new Listener() {
            @Override
            public void process(WatchedEvent event) {
                rootExist = false;
            }
        }));
        System.out.println("----------------------------------------------create root");
    }
    
    void deleteNamespace() throws KeeperException, InterruptedException {
        zooKeeper.delete(rootNode, Constants.VERSION);
    }
    
    void setRootNode(final String rootNode) {
        this.rootNode = rootNode;
    }
    
    void setAuthorities(final String scheme, final byte[] auth) {
        this.scheme = scheme;
        this.auth = auth;
        this.authorities = ZooDefs.Ids.CREATOR_ALL_ACL;
    }
    
    public ClientFactory getClientFactory() {
        return clientFactory;
    }
    
    void setClientFactory(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }
}
