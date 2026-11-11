import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

/**
 * Reproduces a common mail-ingestion pattern:
 *   1. connect to a mailbox over POP3
 *   2. fetch messages
 *   3. walk each message's MIME tree via getContent(), recursing into
 *      nested multiparts (the typical "process every part" loop).
 *
 * With javax.mail:mail 1.4.x (no nesting/part limit) a single crafted
 * email drives unbounded recursion -> StackOverflowError. Because that
 * is an Error, a surrounding catch(Exception) does NOT recover; the
 * ingestion thread dies.
 */
public class MailIngestDos {

    static int depth = 0, maxDepth = 0;

    // identical shape to a typical "process multipart" routine
    static void processMultipart(Multipart mp) throws Exception {
        try {
            for (int i = 0; i < mp.getCount(); i++) {
                Part part = mp.getBodyPart(i);
                try {
                    Object content = part.getContent();
                    if (content instanceof Multipart) {
                        depth++; if (depth > maxDepth) maxDepth = depth;
                        processMultipart((Multipart) content);   // unbounded recursion
                        depth--;
                    } else {
                        processPart(part);
                    }
                } catch (Exception e) {       // Error (StackOverflowError) is NOT caught here
                    processPart(part);
                }
            }
        } catch (Exception ex) { }
    }

    static void processPart(Part part) throws Exception {
        part.getContentType(); part.getDisposition();
        ((MimePart) part).getContentID(); part.getFileName();
    }

    public static void main(String[] a) throws Exception {
        Properties props = System.getProperties();
        props.put("mail.pop3.port", "11110");
        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("pop3");
        store.connect("127.0.0.1", "user", "pw");
        Folder folder = store.getDefaultFolder().getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        Message[] msgs = folder.getMessages();
        System.out.println("[ingest] fetched " + msgs.length + " message(s) via POP3");

        for (Message m : msgs) {
            long t0 = System.currentTimeMillis();
            try {                            // mirrors a per-message catch(Exception)
                Object content = m.getContent();
                if (content instanceof Multipart) processMultipart((Multipart) content);
                System.out.println("[ingest] processed OK in "
                        + (System.currentTimeMillis() - t0) + "ms, depth=" + maxDepth);
            } catch (Exception e) {
                System.out.println("[ingest] caught Exception (recovered): " + e);
            }
        }
        folder.close(false); store.close();
        System.out.println("[ingest] loop completed normally");
    }
}
