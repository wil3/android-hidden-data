package edu.bu.android.hiddendata;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helpers for dealing with configuration files (source/sinks, easy tain, etc..)
 * @author Wil Koch
 *
 */
public class ConfigUtils {

	private static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class.getName());
	
	/**
	 * Create for the second pass so we can start independently
	 * 
	 * @param base A base file used to append to these new source/sinks. Set to null if none is to be used.
	 * @param file The resulting source and sink file
	 * @param sources
	 * @param sinks 
	 */
	public static void createSinkSourceFile(String base, File file,  Set<String> sources, Set<String> sinks){
		
		
		PrintWriter writer = null;
		try {
			//TODO does constructor with only file use default charset? This redudant?
			writer = new PrintWriter(file, Charset.defaultCharset().displayName());
			for (String source : sources){
				String sourceEntry = source + " -> _SOURCE_";
				writer.println(sourceEntry);
			}
			writer.println("");
			for (String sink : sinks){
				String sinkEntry = sink + " -> _SINK_";
				writer.println(sinkEntry);
			}
			

			if (base != null){
				
				writer.println("");
				writer.println("");
				
				//Load all the known UI sinks
				Path path = FileSystems.getDefault().getPath(base);
				List<String> UISinks = Files.readAllLines(path, Charset.defaultCharset());
	
				//Now add all the sinks
				for (String sink : UISinks){
					writer.println(sink);
				}
			}
			
			
		} catch (IOException e){
			logger.error(e.getMessage());
		} finally {
			if (writer != null){
				writer.close();
			}
		}
	}
	
	public static void createEasyTaintWrapperFile(File easyTaintWrapperFile, Set<String> models){
		//Load all the known UI sinks
				
		PrintWriter writer = null;
		try {
			
			writer = new PrintWriter(easyTaintWrapperFile,  Charset.defaultCharset().displayName());
			
			//Include the models
			for (String model : models){
				writer.println("^" + model);
			}
			
			//TODO remove hardcoded path
			Path path = FileSystems.getDefault().getPath("./EasyTaintWrapperSource-default.txt");
			List<String> easyTaints = Files.readAllLines(path, Charset.defaultCharset());

			//Now add all the defaults
			for (String taints : easyTaints){
				writer.println(taints);
			}
			
		} catch (IOException e){
			logger.error(e.getMessage());
		} finally {
			if (writer != null){
				writer.close();
			}
		}
	}
}
