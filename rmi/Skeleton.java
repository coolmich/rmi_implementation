package rmi;

import java.io.InputStream;
import java.net.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.*;
import java.util.Arrays;

/** RMI skeleton

 <p>
 A skeleton encapsulates a multithreaded TCP server. The server's clients are
 intended to be RMI stubs created using the <code>Stub</code> class.

 <p>
 The skeleton class is parametrized by a type variable. This type variable
 should be instantiated with an interface. The skeleton will accept from the
 stub requests for calls to the methods of this interface. It will then
 forward those requests to an object. The object is specified when the
 skeleton is constructed, and must implement the remote interface. Each
 method in the interface should be marked as throwing
 <code>RMIException</code>, in addition to any other exceptions that the user
 desires.

 <p>
 Exceptions may occur at the top level in the listening and service threads.
 The skeleton's response to these exceptions can be customized by deriving
 a class from <code>Skeleton</code> and overriding <code>listen_error</code>
 or <code>service_error</code>.
 */
public class Skeleton<T>
{
    private InetSocketAddress socketAddress = null;
    private boolean running = false;
    private MultiThreadedServer listener = null;
    private Thread listenThread = null;
    private Class<T> c;
    private T server;
    /** Creates a <code>Skeleton</code> with no initial server address. The
     address will be determined by the system when <code>start</code> is
     called. Equivalent to using <code>Skeleton(null)</code>.

     <p>
     This constructor is for skeletons that will not be used for
     bootstrapping RMI - those that therefore do not require a well-known
     port.

     @param c An object representing the class of the interface for which the
     skeleton server is to handle method call requests.
     @param server An object implementing said interface. Requests for method
     calls are forwarded by the skeleton to this object.
     @throws Error If <code>c</code> does not represent a remote interface -
     an interface whose methods are all marked as throwing
     <code>RMIException</code>.
     @throws NullPointerException If either of <code>c</code> or
     <code>server</code> is <code>null</code>.
     */
    public Skeleton(Class<T> c, T server)
    {
        Stub.checkClass(c);
        if(server == null){
            throw new NullPointerException("server is null");
        }
        this.c = c;
        this.server = server;
    }

    /** Creates a <code>Skeleton</code> with the given initial server address.

     <p>
     This constructor should be used when the port number is significant.

     @param c An object representing the class of the interface for which the
     skeleton server is to handle method call requests.
     @param server An object implementing said interface. Requests for method
     calls are forwarded by the skeleton to this object.
     @param address The address at which the skeleton is to run. If
     <code>null</code>, the address will be chosen by the
     system when <code>start</code> is called.
     @throws Error If <code>c</code> does not represent a remote interface -
     an interface whose methods are all marked as throwing
     <code>RMIException</code>.
     @throws NullPointerException If either of <code>c</code> or
     <code>server</code> is <code>null</code>.
     */
    public Skeleton(Class<T> c, T server, InetSocketAddress address)
    {
        Stub.checkClass(c);
        if(server == null){
            throw new NullPointerException("server is null");
        }
        this.c = c;
        this.server = server;
        this.socketAddress = address;
    }

    /** Called when the listening thread exits.

     <p>
     The listening thread may exit due to a top-level exception, or due to a
     call to <code>stop</code>.

     <p>
     When this method is called, the calling thread owns the lock on the
     <code>Skeleton</code> object. Care must be taken to avoid deadlocks when
     calling <code>start</code> or <code>stop</code> from different threads
     during this call.

     <p>
     The default implementation does nothing.

     @param cause The exception that stopped the skeleton, or
     <code>null</code> if the skeleton stopped normally.
     */
    protected void stopped(Throwable cause)
    {
    }

    /** Called when an exception occurs at the top level in the listening
     thread.

     <p>
     The intent of this method is to allow the user to report exceptions in
     the listening thread to another thread, by a mechanism of the user's
     choosing. The user may also ignore the exceptions. The default
     implementation simply stops the server. The user should not use this
     method to stop the skeleton. The exception will again be provided as the
     argument to <code>stopped</code>, which will be called later.

     @param exception The exception that occurred.
     @return <code>true</code> if the server is to resume accepting
     connections, <code>false</code> if the server is to shut down.
     */
    protected boolean listen_error(Exception exception)
    {
        return false;
    }

    /** Called when an exception occurs at the top level in a service thread.

     <p>
     The default implementation does nothing.

     @param exception The exception that occurred.
     */
    protected void service_error(RMIException exception)
    {
    }

    /** Starts the skeleton server.

     <p>
     A thread is created to listen for connection requests, and the method
     returns immediately. Additional threads are created when connections are
     accepted. The network address used for the server is determined by which
     constructor was used to create the <code>Skeleton</code> object.

     @throws RMIException When the listening socket cannot be created or
     bound, when the listening thread cannot be created,
     or when the server has already been started and has
     not since stopped.
     */
    public synchronized void start() throws RMIException {
        if (this.running) {
            throw new RMIException("server has already been started and has not since stopped");
        }
        try {
            if(this.socketAddress == null){
                ServerSocket tmpSocket = new ServerSocket(0);
                this.socketAddress = new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(),
                        tmpSocket.getLocalPort());
                tmpSocket.close();
            }
            this.listener = new MultiThreadedServer(this.c, this.server, this.socketAddress);
            this.listenThread = new Thread(listener);
            this.listenThread.start();
            while(this.listener.isStopped()){}
        } catch (Exception e) {
            throw new RMIException("listening thread cannot be created", e);
        }
        this.running = true;

    }

    /** Stops the skeleton server, if it is already running.

     <p>
     The listening thread terminates. Threads created to service connections
     may continue running until their invocations of the <code>service</code>
     method return. The server stops at some later time; the method
     <code>stopped</code> is called at that point. The server may then be
     restarted.
     */
    public synchronized void stop()
    {
        if(this.listener != null && !this.listener.isStopped()){
            this.listener.stop();
            try{
                this.listenThread.join();
                this.running = false;
                stopped(null);
            }
            catch ( InterruptedException e ) {
                stopped(e);
                e.printStackTrace();
            }
        }
    }

    public InetSocketAddress getAddress(){
        return this.socketAddress;
    }

    public boolean isRunning(){
        return this.running;
    }


    protected class MultiThreadedServer<T> implements Runnable{

        protected int          serverPort   = 8080;
        protected ServerSocket serverSocket = null;
        protected boolean      isStopped    = true;
        protected Class<T> c;
        protected T server = null;

        public MultiThreadedServer(Class<T> c, T server, InetSocketAddress address){
            this.serverPort = address.getPort();
            this.c = c;
            this.server = server;
        }

        public void run(){
            openServerSocket();
            this.isStopped = false;
            while(!this.isStopped){
                Socket clientSocket = null;
                try {
                    clientSocket = this.serverSocket.accept();
                } catch (IOException e) {
                    if(isStopped()) {
                        return;
                    }
                    throw new RuntimeException(
                            "Error accepting client connection", e);
                }
                new Thread(
                        new WorkerRunnable(
                                clientSocket, this.c, this.server)
                ).start();
            }
        }


        public synchronized boolean isStopped() {
            return this.isStopped;
        }

        public synchronized void stop(){
            if(!this.isStopped) {
                this.isStopped = true; //?
                try {
                    this.serverSocket.close();
                } catch (IOException e) {
                    throw new RuntimeException("Error closing server", e);
                }
            }
        }

        private void openServerSocket() {
            try {
                this.serverSocket = new ServerSocket(this.serverPort);
            } catch (Exception e) {
                throw new RuntimeException("Cannot open port "+ this.serverPort, e);
            }
        }

    }

    protected class WorkerRunnable<T> implements Runnable{

        protected Socket clientSocket = null;
        protected Class<T> c;
        protected T server = null;


        public WorkerRunnable(Socket clientSocket, Class<T> c, T server) {
            this.clientSocket = clientSocket;
            this.server   = server;
            this.c = c;
        }

        private <T>boolean checkRemoteInterface(Class<T> c)
        {
            if(c == null) return false;
            if(!c.isInterface()) return false;
            for(Method method: c.getDeclaredMethods()){
                if(!Arrays.asList(method.getExceptionTypes()).contains(RMIException.class)){
                    return false;
                }
            }
            return true;
        }

        public void run() {
            ObjectOutputStream output = null;
            ObjectInputStream input = null;
            try {
                output = new ObjectOutputStream(clientSocket.getOutputStream());
                output.flush();
                InputStream stream = clientSocket.getInputStream();
                input = new ObjectInputStream(stream);
                String methodName = (String) input.readObject();
                Class parameterTypes[] = (Class[]) input.readObject();
                Object[] args = (Object[]) input.readObject();

                Method method = c.getMethod(methodName, parameterTypes);
                Class returnType = method.getReturnType();
                Object return_value;
                try {
                    method.setAccessible(true);
                    return_value = method.invoke(server, args);

                    output.writeObject("OK");
                    if(!returnType.equals(Void.TYPE)) {
                        if (checkRemoteInterface(returnType)) {
                            Skeleton skeleton = new Skeleton(returnType, return_value);
                            skeleton.start();
                            output.writeObject(Stub.create(returnType, skeleton.getAddress()));
                        } else {
                            output.writeObject(return_value);
                        }
                    }
                } catch (InvocationTargetException e) {
                    output.writeObject("fail");
                    output.writeObject(e.getTargetException());
                }
            } catch (Exception e) {
                //report exception somewhere.
                service_error(new RMIException(e));
            } finally {
                try {
                    if (output != null) {
                        output.flush();
                        output.close();
                    }
                    if (input != null) {
                        input.close();
                    }
                    this.clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
