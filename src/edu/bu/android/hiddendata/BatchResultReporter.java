package edu.bu.android.hiddendata;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.PatternFilenameFilter;

import edu.bu.android.hiddendata.model.JsonUtils;
import edu.bu.android.hiddendata.model.Results;

public class BatchResultReporter {
	private static final Logger logger = LoggerFactory.getLogger("Result");
	private final File resultsDir;
	private List<File> resultDirs = new ArrayList<File>();
	
	public BatchResultReporter(String resultsDir){
		this.resultsDir = new File(resultsDir);
	}
	
	public void loadFromFile(String file){
		Path path = FileSystems.getDefault().getPath(file);
		try {
			List<String> apks = Files.readAllLines(path, Charset.defaultCharset());
			for (String apk : apks){
				resultDirs.add(new File(resultsDir, apk));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void loadAll(){
		String[] dirFiles = resultsDir.list();
		for (String dir : dirFiles){
			resultDirs.add(new File(resultsDir, dir));
		}
	}
	public void getFinishedWithResult(boolean display){
		List<File> resultFiles = new ArrayList<File>();
		
		for (File apk : resultDirs){
			String [] resultFile = apk.list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return (name.endsWith(FindHidden.RESULTS_SUFFIX));
				}
			});
			if (resultFile.length == 1){
				resultFiles.add(new File(apk, resultFile[0]));
			}
		}
		
		logger.info("{} results found", resultFiles.size());
		if (display){
			processResults(resultFiles);
		}
	}
	
	public void listFinished(){
		List<File> resultFiles = getFilesByRegex(FindHidden.RESULTS_SUFFIX + "$");
		display(resultFiles, true);
	}
	
	public int getFinished(){
		List<File> resultFiles = new ArrayList<File>();
		String[] dirFiles = resultsDir.list();
		for (String dir : dirFiles){
			File apkDirFile = new File(resultsDir, dir);
			String [] resultFile = apkDirFile.list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return (name.equals(FindHidden.FLAG_DONE));
				}
			});
			
			if (resultFile.length == 1){
				resultFiles.add(apkDirFile);
			}
		}
		logger.info("{} finished", resultFiles.size());
		return resultFiles.size();
	}
	
	public void getCrashed(boolean display){
		//String resultsDir = "";
		List<File> crashedFiles = new ArrayList<File>();
		String[] dirFiles = resultsDir.list();
		for (String dir : dirFiles){
			File apkDirFile = new File(resultsDir, dir);
			String [] resultFile = apkDirFile.list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return (name.endsWith(".flag"));
				}
			});
			
			if (resultFile.length == 0){ // no flags so we crashed in the beginnging for some reason
				crashedFiles.add(apkDirFile);
			}
		}
		logger.info("{} probably crashes.", crashedFiles.size());
		if (display){
			display(crashedFiles, false);
		}
	}
	
	
	public void getOutOfMemory(boolean display){
		List<File> files = getFiles("java_error", "log");
		logger.info("{} ran out of memory", files.size());
	}
	
	/**
	 * The APKs that were able to successfully finsh the first pass and have a model found
	 */
	public void getFoundNetworkToModelFlows(boolean display){
		List<File> files = getFiles(FindHidden.FLAG_MODEL, "");
		logger.info("{} APKS have network to model flows");
		if (display){
			display(files, false);
		}
	}
	
	/**
	 * Get files in the results directory that match 
	 * @param filePrefix
	 * @param fileSuffix
	 * @return
	 */
	private List<File> getFiles(final String filePrefix, final String fileSuffix){
		//String resultsDir = "";
		List<File> files = new ArrayList<File>();
		for (File apk : resultDirs){
			String [] resultFile = apk.list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith(filePrefix) && name.endsWith(fileSuffix);
				}
			});
			
			if (resultFile.length > 0){ // no flags so we crashed in the beginnging for some reason
				files.add(apk);
			}
		}
		return files;
	}
	private List<File> getFilesByRegex(String regex){
		final Pattern p = Pattern.compile(regex);
		//String resultsDir = "";
		List<File> files = new ArrayList<File>();
		for (File apk : resultDirs){
			String [] resultFile = apk.list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return p.matcher(name).find();
				}
			});
			
			if (resultFile.length > 0){ // no flags so we crashed in the beginnging for some reason
				files.add(apk);
			}
		}
		return files;
	}
	private void processResults(List<File> results){
		for (File f : results){
			Results result = new JsonUtils<Results>().load(f, Results.class);
			displayResult(result);
		}
	}
	private void display(List<File> files, boolean justName){
		for (File f : files){
			if (justName){
				System.out.println(f.getName());
			} else {
				System.out.println(f.getAbsolutePath());
			}
		}
	}
	private void displayResult(Results result){
		logger.info(result.getApkName());
		Iterator<String> it = result.getGetMethodsInApp().keySet().iterator();
		while (it.hasNext()){
			String key = it.next();
			int count = result.getGetMethodsInApp().get(key);
			
			if (!result.getUsedConfidenceHigh().contains(key) 
					&& !result.getUsedConfidenceLow().contains(key)){
				//result.getGetMethodsInApp().remove(key);
				logger.info(key);
			}
		}
	}
	public static void main(String[] args){
		

		CommandLineParser parser = new DefaultParser();

		Options options = new Options();
		options.addOption("r", "results", true, "Path to results directory");

		options.addOption("d", "display", true, "Display status of apks in directory");
		
		options.addOption("s", "Number of APKs with results");
		options.addOption("S", "Detail list of models for APKs with results");
		options.addOption("f", "List of all apk files with results");
		
		options.addOption("l", "List runs that crashed");
		options.addOption("L", "Detail list displaying all apks that crashed");
		
		options.addOption("m", "Out of memory");
		options.addOption("M", "Out of memory, list those files");
		
		options.addOption("n", "Network to model flows found");
		options.addOption("N", "List of APKs where network to model flows found");
		
		
		options.addOption("t", "Total number of APKs processed");

	    try {
			CommandLine line = parser.parse( options, args );
			
			BatchResultReporter report = new BatchResultReporter(line.getOptionValue("results"));
			
			if (line.hasOption("display")){
				report.loadFromFile(line.getOptionValue("display"));
			} else {
				report.loadAll();
			}
			
			
			if (line.hasOption("n")){
				report.getFoundNetworkToModelFlows(false);
			} else if (line.hasOption("N")){
				report.getFoundNetworkToModelFlows(true);
			}
			
			if (line.hasOption("l")){
				report.getCrashed(false);
			} else if (line.hasOption("L")){
				report.getCrashed(true);
			} 
			
			if (line.hasOption("t")){
				report.getFinished();
			}
			
			if (line.hasOption("s")){
				report.getFinishedWithResult(false);
			} else if (line.hasOption("S")){
				report.getFinishedWithResult(true);
			} else if (line.hasOption("f")){
				report.listFinished();
			}
			
			if (line.hasOption("m")){
				report.getOutOfMemory(false);
			} else if (line.hasOption("M")){
				report.getOutOfMemory(true);
			}
			
		} catch (ParseException e) {
			e.printStackTrace();
		}

		
	}
}

