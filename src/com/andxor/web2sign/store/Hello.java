package com.andxor.web2sign.store;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Example storage provider for <a href="https://www.andxor.it/w2s/native/">Web2Sign</a>.
 */
public class Hello extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // defined server-side
        String url;
        try {
            url = (String) Util.getConfig().get("url");
        } catch (Exception e) {
            throw new RuntimeException("Configuration error in 'url'", e);
        }
        // generate a local session
        String token = Store.generate();
        // send the result to the user
        response.setContentType("text/html");
        response.setHeader("Cache-Control", "max-age=0");
        String goTo = url + "-" + token;
        String file = request.getParameter("file");
        if (file != null)
            goTo += "#" + file;
        response.getWriter().write("<!DOCTYPE html>\n"
                + "<html>"
                + "<head>\n"
                + "  <title>Web2Sign Demo</title>\n"
                + "  <meta http-equiv='X-UA-Compatible' content='IE=edge,chrome=1'>\n"
                + "  <meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,minimum-scale=1,user-scalable=no'>\n"
                + "  <style>\n"
                + "    iframe {\n"
                + "      position: absolute;\n"
                + "      top: 0;\n"
                + "      left: 0;\n"
                + "      width: 100%;\n"
                + "      height: 100%;\n"
                + "      border: 0;\n"
                + "    }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<iframe src='" + Util.specialChars(goTo) + "'></iframe>\n"
                + "</body>\n"
                + "</html>\n");
//      response.sendRedirect(response.encodeRedirectURL(goTo));
    }

}
