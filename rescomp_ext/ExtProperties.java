package sgdk.rescomp.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ExtProperties {

	public static final String MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX = 	"MAX_TILESET_NUM_FOR_MAP_BASE_TILE_INDEX";
	public static final String STARTING_TILESET_ON_SGDK = 					"STARTING_TILESET_ON_SGDK";
	public static final String MAX_TILESET_CHUNK_SIZE = 					"MAX_TILESET_CHUNK_SIZE";
	public static final String MAX_TILESET_SIZE_ALLOWED_FOR_SPLIT_IN_2 = 	"MAX_TILESET_SIZE_ALLOWED_FOR_SPLIT_IN_2";

	private static ExtProperties instance;
	private Properties properties[] = {null, null};

	private ExtProperties () {
	}

	private static final ExtProperties getInstance() {
		if (instance == null) {
			synchronized (ExtProperties.class) {
				if (instance == null) {
					instance = new ExtProperties();
					instance.properties[0] = readProperties("ext.processor.properties", "sgdk/rescomp/processor/");
					instance.properties[1] = readProperties("ext.resource.properties", "sgdk/rescomp/resource/");
					return instance;
				}
			}
		}
		return instance;
	}

	private static final Properties readProperties(String fileName, String atPackage) {
		Properties props = null;

		// Firstly try to load as a file
		File jarDir = new File(ExtProperties.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile();
        File propertiesFile = new File(jarDir, fileName);
        try (FileInputStream inputStream = new FileInputStream(propertiesFile)) {
        	props = new Properties();
            props.load(inputStream);
        } catch (Exception e) {
			props = null;
		}

		if (props != null)
			return props;

		// Lastly try to load as a resource
		try (InputStream inputStream = ExtProperties.class.getClassLoader().getResourceAsStream(atPackage + fileName)) {	
			props = new Properties();
			props.load(inputStream);
		} catch (Exception e) {
			props = null;
		}

		if (props == null)
			throw new RuntimeException("COULDN'T LOAD PROPERTIES FILE: " + fileName);

		return props;
	}

	public static final String getString(String key) {
		for (Properties props : getInstance().properties) {
			if (props != null) {
				if (props.containsKey(key))
					return props.getProperty(key);
			}
		}
		return null;
	}

	public static final int getInt(String key) {
		for (Properties props : getInstance().properties) {
			if (props != null) {
				if (props.containsKey(key))
					return Integer.parseInt(props.getProperty(key));
			}
		}
		return Integer.MIN_VALUE;
	}

}
