package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.Resource;

/**
 * Just add a custom content to the auto generated header file.
 */
public class HeaderAppender extends Resource
{
    final int hc;
    final String content;

    public HeaderAppender(String id, String headerContent) throws Exception
    {
        super(id);
        
        content = headerContent;
        // compute hash code
        hc = headerContent.hashCode();
    }

	@Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof HeaderAppender)
        {
            final HeaderAppender ha = (HeaderAppender) obj;
            return content.equals(ha.content);
        }

        return false;
    }

    @Override
    public List<Bin> getInternalBinResources()
    {
        return Collections.emptyList();
    }

    @Override
    public int shallowSize()
    {
        return 0;
    }

    @Override
    public int totalSize()
    {
        return 0;
    }

    @Override
    public void out(ByteArrayOutputStream outB, StringBuilder outS, StringBuilder outH)
    {
    	String realContent = content.replace("\\n", System.lineSeparator()).replace("\\t", "\t").replace("\\s", " ");
    	outH.append(realContent).append(System.lineSeparator());
    }
}
