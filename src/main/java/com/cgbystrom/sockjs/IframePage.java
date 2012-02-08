package com.cgbystrom.sockjs;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class IframePage {
    private ChannelBuffer content;
    private String etag;
    
    public IframePage(String url) {
        content = createContent(url);
    }

    public void handle(HttpRequest request, HttpResponse response) {
        QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        String path = qsd.getPath();

        if (!path.matches(".*/iframe[0-9-.a-z_]*.html")) {
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            response.setContent(ChannelBuffers.copiedBuffer("Not found", CharsetUtil.UTF_8));
            return;
        }

        response.setHeader(HttpHeaders.Names.SET_COOKIE, "JSESSIONID=dummy; path=/");

        if (request.containsHeader(HttpHeaders.Names.IF_NONE_MATCH)) {
            response.setStatus(HttpResponseStatus.NOT_MODIFIED);
            response.removeHeader(HttpHeaders.Names.CONTENT_TYPE);
        } else {
            response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
            response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "max-age=31536000, public");
            response.setHeader(HttpHeaders.Names.EXPIRES, "FIXME"); // FIXME: Fix this
            response.removeHeader(HttpHeaders.Names.SET_COOKIE);
            response.setContent(content);
        }

        response.setHeader(HttpHeaders.Names.ETAG, etag);
    }
    
    private ChannelBuffer createContent(String url) {
        String content = "<!DOCTYPE html>\n" +
        "<html>\n" +
        "<head>\n" +
        "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
        "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
        "  <script>\n" +
        "    document.domain = document.domain;\n" +
        "    _sockjs_onload = function(){SockJS.bootstrap_iframe();};\n" +
        "  </script>\n" +
        "  <script src=\"" + url + "\"></script>\n" +
        "</head>\n" +
        "<body>\n" +
        "  <h2>Don't panic!</h2>\n" +
        "  <p>This is a SockJS hidden iframe. It's used for cross domain magic.</p>\n" +
        "</body>\n" +
        "</html>";

        // FIXME: Don't modify attributes here
        etag = "\"" + generateMd5(content) + "\"";
        
        return ChannelBuffers.copiedBuffer(content, CharsetUtil.UTF_8);
    }

    private static String generateMd5(String value) {
        String encryptedString = null;
        byte[] bytesToBeEncrypted;
        try {
            // convert string to bytes using a encoding scheme
            bytesToBeEncrypted = value.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] theDigest = md.digest(bytesToBeEncrypted);
            // convert each byte to a hexadecimal digit
            Formatter formatter = new Formatter();
            for (byte b : theDigest) {
                formatter.format("%02x", b);
            }
            encryptedString = formatter.toString().toLowerCase();
        } catch (UnsupportedEncodingException e) {
            // FIXME: Return proper HTTP error
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // FIXME: Return proper HTTP error
            e.printStackTrace();
        }
        return encryptedString;
    }
}
