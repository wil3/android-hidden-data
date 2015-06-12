package edu.bu.android.hiddendata.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class JsonUtils {


	/**
	 * Load the parameter mappings
	 * @return
	 */
	public static FirstPassResult loadFirstPassResultFile(File file){
		FirstPassResult map = null;
		try {
			JsonReader reader = new JsonReader(new FileReader(file));
			Gson gson = new Gson();
			map = gson.fromJson(reader, FirstPassResult.class);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return map;
	}
}
