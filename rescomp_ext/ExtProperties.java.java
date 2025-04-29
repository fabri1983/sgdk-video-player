package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.type.CompressionCustom;

/**
 * Just add a custom content to the auto generated header file.
 */
public class HeaderAppenderCompressionCustom extends Resource
{
    final int hc;
    final String content;

    public HeaderAppenderCompressionCustom(String id) throws Exception
    {
        super(id);
        
        StringBuilder sb = new StringBuilder(256);
        for (CompressionCustom cc : CompressionCustom.values()) {
        	if (cc == CompressionCustom.NONE)
        		continue;
        	sb.append("#define ").append(cc.getValue()).append(" ").append(cc.getDefineValue()).append(System.lineSeparator());
        }

        content = sb.toString();

        // compute hash code
        hc = content.hashCode();
    }

	@Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof HeaderAppenderCompressionCustom)
        {
            final HeaderAppenderCompressionCustom other = (HeaderAppenderCompressionCustom) obj;
            return content.equals(other.content);
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
    	outH.append(content).append(System.lineSeparator());
    }
}
