package com.andxor.web2sign.store;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example storage provider for <a href="https://www.andxor.it/w2s/native/">Web2Sign</a>.
 */
public class Store extends HttpServlet {

    private static final Pattern PATH  = Pattern.compile("/(?:[0-9A-Za-z]+-)?([0-9A-Za-z]+)/(?:([0-9]+)(?:/(hook[.]js|qr[.]png))?)?");
    private static final String SCRIPT = "hook.js";
    private static final int MAX_AGE = 3600;

    private static final Logger log = LoggerFactory.getLogger(Store.class);

    /* example session management, use a "real" one for production */
    private final static int MAX_SESSIONS = 100;
    private final static Map<String, ArrayList<JSON.Obj>> sessions = new LinkedHashMap<String, ArrayList<JSON.Obj>>(MAX_SESSIONS + 1, .75F, true) {
        @Override
        public boolean removeEldestEntry(Map.Entry<String, ArrayList<JSON.Obj>> eldest) {
            return size() > MAX_SESSIONS;
        }
    };

    private ArrayList<JSON.Obj> authenticate(String token) {
        return sessions.get(token);
    }

    protected static String generate() {
        // create initial state of a new session
        ArrayList<JSON.Obj> files = new ArrayList<JSON.Obj>();
        try {
            for (Object o : (Object[]) Util.getConfig().get("files"))
                files.add((JSON.Obj) o);
        } catch (Exception e) {
            throw new RuntimeException("Configuration error in 'files'", e);
        }
        // create session
        String token = Util.uniqueToken();
        sessions.put(token, files);
        return token;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.info("Request: " + request.getPathInfo());
        Matcher pathMatcher = PATH.matcher(request.getPathInfo());
        if (!pathMatcher.matches()) {
            log.warn("Uso errato della servlet: " + request.getPathInfo());
            response.sendError(400, "Richiesta non valida");
            return;
        }
        final String token = pathMatcher.group(1);
        final String file = pathMatcher.group(2);
        final String extra = pathMatcher.group(3);
        ArrayList<JSON.Obj> files = authenticate(token);
        if (files == null) {
            log.warn("Token errato");
            response.sendError(400, "Richiesta non valida");
        }
        try {
            if (file == null) {
                // with no file parameter, we're sending the list of available files
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "max-age=0"); // list changes in time
                JSON.encode(response.getOutputStream(), JSON.obj("script", SCRIPT, "files", files), true);
            } else if (extra == null) {
                // with file parameter, we're sending the file content
                response.setContentType("application/octet-stream");
                response.setHeader("Cache-Control", "max-age=" + MAX_AGE); // files are added but never change
                int num = Integer.parseInt(file);
                Util.inToOut(
                        new FileInputStream(Util.getFile((String) files.get(num).get("filename"))),
                        response.getOutputStream());
            } else {
                // with extra parameter, we're sending the example static files
                response.setContentType(extra.endsWith(".png") ? "image/png" : "text/javascript");
                response.setHeader("Cache-Control", "max-age=" + MAX_AGE);
                Util.inToOut(getServletContext().getResourceAsStream("/" + extra), response.getOutputStream());
            }
        } catch (Throwable t) {
            log.error("Errore", t);
            response.sendError(500, "Errore");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.info("Request: " + request.getPathInfo());
        Matcher pathMatcher = PATH.matcher(request.getPathInfo());
        if (!pathMatcher.matches()) {
            log.warn("Uso errato della servlet: " + request.getPathInfo());
            response.sendError(400, "Invalid request");
            return;
        }
        try {
            JSON.Obj params = new JSON.Parser(request.getHeader("X-W2S-JSON"), false).getObject();
            // these parameters can be used to know what has been done by web2sign in this update
            // "Hash" is the old document hash (to know which one in the document array it is)
            // "Hash-New" is the new hash, to check it was received properly
            // "Operation" is what was done
            // "Field" is the field on which the peration was done
            log.debug("Received headers: " + JSON.encode(params, true, true));
            /*
            Received headers: {
              "Hash": "1B8D882EC4CC04C4FD5D4100DF322EAE3FB1BE3D1F752297846A027644B8B25B",
              "Operation": "add+sign",
              "Field": "Signature1",
              "Hash-New": "E28DDE55AFCCA6A1A7F260351D1D38871A0A7B4CED3905C30DF1B946ECC6AD66"
            }
            */
        } catch (Exception e) {
            log.error("web2sign didn’t return valid headers", e);
            response.sendError(500, "Errore");
        }

        final String token = pathMatcher.group(1);
        final String file = pathMatcher.group(2);
        final int num = Integer.parseInt(file);
        ArrayList<JSON.Obj> files = authenticate(token);
        if (files == null) {
            log.warn("Token errato");
            response.sendError(400, "Invalid request");
        }
        try {
            // in this example we have an array of files, and we decide to add any document update at the end of the array
            // in single-document examples it would be more common to just replace the only file with new version
            byte[] data = Util.inToArray(request.getInputStream());
            JSON.Obj fileInfo = files.get(num);
            String oldFilename = fileInfo.getString("filename");
            File newFile = Util.newFile(oldFilename);
            FileOutputStream fos = new FileOutputStream(newFile);
            fos.write(data);
            fos.close();
            files.add(JSON.Obj.merge(fileInfo, JSON.obj(
                    "filename", newFile.getName(),
                    "hash", Util.toHex(Util.arrayToHash(data))
            )));
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "max-age=0");
            JSON.encode(response.getOutputStream(), JSON.obj("files", files), true);
        } catch (Throwable t) {
            log.error("Error", t);
            response.sendError(500, "Error");
        }
    }

}
