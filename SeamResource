package ir.seam.ui.core;

import static org.jboss.seam.ScopeType.APPLICATION;
import static org.jboss.seam.annotations.Install.BUILT_IN;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.jboss.seam.annotations.Create;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.jboss.seam.web.AbstractResource;

@Scope(APPLICATION)
@Name("seamResource")
@Install(precedence = BUILT_IN)
@BypassInterceptors
public class SeamResource extends AbstractResource
{

    private long startupTime;

    @Create
    public void init()
    {
        this.startupTime = System.currentTimeMillis();
    }

    @Override
    public void getResource(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        
        URL resourceUrl = SeamResource.class.getResource("/ir/seam/ui/core/test.js");
        long latestTimestamp = -1;
        long timestamp = getFileTimestamp(resourceUrl);
        if (timestamp > latestTimestamp)
        {
            latestTimestamp = timestamp;
        }
        long lastModified = (latestTimestamp > this.startupTime ? latestTimestamp : this.startupTime);
        
        if (request.getDateHeader("If-Modified-Since") != -1)
        {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        if (lastModified != -1)
        {
            lastModified -= lastModified % 1000;
            long requestModifiedSince = request.getDateHeader("If-Modified-Since");
            if (lastModified <= requestModifiedSince)
            {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
        }
        response.setDateHeader("Last-Modified", lastModified != -1 ? lastModified : this.startupTime);
        response.setHeader("Cache-Control", "must-revalidate");
        InputStream in = null;
		OutputStream outputStream = null;
        try
        {
            in = resourceUrl.openStream();
            response.setContentType("text/javascript");
            outputStream = selectOutputStream(request, response);
            if (in != null)
            {
                byte[] buffer = new byte[1024];
                int read = in.read(buffer);
                while (read != -1)
                {
                    outputStream.write(buffer, 0, read);
                    read = in.read(buffer);
                }
                outputStream.flush();
                
            }
            else
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
        finally
        {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(outputStream);
        }

    }

    protected long getFileTimestamp(URL url)
    {

        try
        {
            URLConnection resource = url.openConnection();
            long lastModifiedTime = resource.getLastModified();
            // if (logger.isDebugEnabled()) {
            // logger.debug("Last-modified timestamp of " + resource + " is " +
            // lastModifiedTime);
            // }
            return lastModifiedTime;
        }
        catch (IOException ex)
        {
            // logger.warn("Couldn't retrieve last-modified timestamp of [" +
            // resource +
            // "] - using ResourceServlet startup time");
            return -1;
        }
    }

    @Override
    public String getResourcePath()
    {
        return "/components.js";
    }

}
