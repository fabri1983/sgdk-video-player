package sgdk.rescomp.resource;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import sgdk.rescomp.Resource;
import sgdk.rescomp.type.CustomDataTypes;

/**
 * Just add a custom content to the auto generated header file.
 */
public class HeaderAppenderAllCustomResource extends Resource
{
    final int hc;
    final CustomDataTypes[] selection;

    public HeaderAppenderAllCustomResource(String id, CustomDataTypes[] selection) throws Exception
    {
        super(id);
        
        this.selection = selection;

        // compute hash code
        hc = selection.hashCode();
    }

	@Override
    public int internalHashCode()
    {
        return hc;
    }

    @Override
    public boolean internalEquals(Object obj)
    {
        if (obj instanceof HeaderAppenderAllCustomResource)
        {
            final HeaderAppenderAllCustomResource other = (HeaderAppenderAllCustomResource) obj;
            return selection.equals(other.selection);
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
    	if (selection.length == 0)
    		return;

    	StringBuilder sb = new StringBuilder(1024);
    	for (CustomDataTypes cdt : selection) {
			sb.append(CustomDataTypes.getDefinition(cdt)).append(System.lineSeparator());
		}

    	outH.append(sb.toString());
    }
}
