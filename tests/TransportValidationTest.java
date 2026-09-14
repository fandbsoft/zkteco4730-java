package zkteco;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
public class TransportValidationTest {
 public static void main(String[] args)throws Exception {
  byte[] large=new byte[200000];Arrays.fill(large,(byte)255);
  byte[] raw=new ZKTeco4370_ZkPacket(1501,1,2,large).toZkBytes();
  long sum=0;for(int i=0;i<raw.length;i+=2)sum+=(raw[i]&255)|((raw[i+1]&255)<<8);
  while((sum>>>16)>0)sum=(sum&65535)+(sum>>>16);
  if(sum!=65535)throw new AssertionError("large checksum");
  ZKTeco4370_ZkPacket.parseZkBytes(raw,0,raw.length);
  raw[100]^=1;
  try{ZKTeco4370_ZkPacket.parseZkBytes(raw,0,raw.length);throw new AssertionError("corrupt accepted");}catch(IllegalArgumentException expected){}
  try{ZKTeco4370_ZkPacket.parseZkBytes(raw,-1,8);throw new AssertionError("bounds");}catch(IllegalArgumentException expected){}
  for(int mode=0;mode<3;mode++) {
   final int m=mode;
   try(ServerSocket server=new ServerSocket(0)) {
    var peer=CompletableFuture.runAsync(()->{try(Socket s=server.accept()) {
     s.setSoTimeout(3000);
     PhotoDownloadTest.read(s);PhotoDownloadTest.send(s,2000,42,new byte[0]);
     if(PhotoDownloadTest.read(s).getCommandId()!=1502)throw new AssertionError("free");
     PhotoDownloadTest.send(s,2000,42,new byte[0]);
     if(PhotoDownloadTest.read(s).getCommandId()!=1503)throw new AssertionError("request");
     if(m==1)PhotoDownloadTest.send(s,4989,42,new byte[0]);
     else {
      PhotoDownloadTest.send(s,2000,42,new byte[]{4,0,0,0});
      if(PhotoDownloadTest.read(s).getCommandId()!=1504)throw new AssertionError("read");
      PhotoDownloadTest.send(s,1500,42,new byte[]{4,0,0,0});
      PhotoDownloadTest.send(s,1501,0,m==2?new byte[5]:new byte[]{1,2,3,4});
     }
     if(PhotoDownloadTest.read(s).getCommandId()!=1502)throw new AssertionError("repeated read instead of free");
     PhotoDownloadTest.send(s,2000,42,new byte[0]);
     if(PhotoDownloadTest.read(s).getCommandId()!=1001)throw new AssertionError("EXIT missing");
    }catch(Exception ex){throw new CompletionException(ex);}});
    try(var zk=new ZKTeco4370_ZkClient("127.0.0.1",server.getLocalPort(),0,3000,3000)) {
     Class<?> handler=Class.forName("zkteco.ZKTeco4370_ZkClient$ZKTeco4370_PayloadHandler");
     ByteArrayOutputStream bytes=new ByteArrayOutputStream();
     Object proxy=java.lang.reflect.Proxy.newProxyInstance(handler.getClassLoader(),new Class[]{handler},(o,method,a)->{
      if(method.getName().equals("onChunk"))bytes.write((byte[])a[0],(int)a[1],(int)a[2]);return null;
     });
     Method method=ZKTeco4370_ZkClient.class.getDeclaredMethod("streamBufferedPayload",byte[].class,String.class,handler);method.setAccessible(true);
     try{method.invoke(zk,new byte[11],"test",proxy);if(m!=0)throw new AssertionError("error swallowed");}
     catch(InvocationTargetException ex){if(m==0 || !(ex.getCause() instanceof IOException))throw ex;}
     if(m==0 && !Arrays.equals(bytes.toByteArray(),new byte[]{1,2,3,4}))throw new AssertionError("data");
    }
    peer.get(5,TimeUnit.SECONDS);
   }
  }
  System.out.println("PASS: checksum, corrupt packet, bounds, PREPARE/DATA, device error, oversized chunk and EXIT");
 }
}
