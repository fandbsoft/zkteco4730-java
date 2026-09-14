package zkteco;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
public class PhotoDownloadTest {
 static final byte[] JPG={(byte)255,(byte)216,1,2,3,4,(byte)255,(byte)217};
 static ZKTeco4370_ZkPacket read(Socket s)throws Exception {
  byte[] h=s.getInputStream().readNBytes(8);
  int len=ByteBuffer.wrap(h,4,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
  byte[] b=s.getInputStream().readNBytes(len);return ZKTeco4370_ZkPacket.parseZkBytes(b,0,b.length);
 }
 static void send(Socket s,int cmd,int index,byte[] b)throws Exception {
  s.getOutputStream().write(new ZKTeco4370_ZkPacket(cmd,index,1,b).toTcpFrame(ZKTeco4370_ZkConstants.TCP_MAGIC));
 }
 public static void main(String[] args)throws Exception {
  for(int mode=0;mode<6;mode++) {
   final int m=mode;
   try(ServerSocket server=new ServerSocket(0)) {
    CompletableFuture<Void> peer=CompletableFuture.runAsync(()->{try(Socket s=server.accept()) {
     s.setSoTimeout(3000);read(s);send(s,2000,42,new byte[0]);
     var req=read(s);
     if(req.getCommandId()!=10010 || !Arrays.equals(req.getPayload(),"001.jpg\0".getBytes(StandardCharsets.US_ASCII)))throw new AssertionError("request");
     if(m==0)send(s,1501,42,JPG);
     else if(m==2)send(s,4985,42,new byte[0]);
     else if(m==3)send(s,1501,42,new byte[]{1,2,3,4});
     else {
      send(s,1500,42,ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(8).putInt(4).array());
      send(s,1501,1,Arrays.copyOfRange(JPG,4,8));
      if(m==1)send(s,1501,0,Arrays.copyOfRange(JPG,0,4));
      if(m==4)send(s,1501,1,Arrays.copyOfRange(JPG,4,8));
     }
    }catch(Exception e){throw new CompletionException(e);}});
    try(var zk=new ZKTeco4370_ZkClient("127.0.0.1",server.getLocalPort(),0,3000,3000)) {
     try {byte[] result=zk.downloadUserPhoto("001");if(m>1 || !Arrays.equals(result,JPG))throw new AssertionError("result");}
     catch(IOException e){if(m<2)throw e;}
     if(zk.isConnected())throw new AssertionError("connection not isolated");
    }
    peer.get(5,TimeUnit.SECONDS);
   }
  }
  for(String id:new String[]{null,"","../1","1.jpg","1\0","x".repeat(25)}) {
   try(var zk=new ZKTeco4370_ZkClient("127.0.0.1")) {
    try{zk.downloadUserPhoto(id);throw new AssertionError("invalid ID accepted");}catch(IllegalArgumentException expected){}
   }
  }
  System.out.println("PASS: 6 transfer scenarios and 6 invalid IDs");
 }
}
