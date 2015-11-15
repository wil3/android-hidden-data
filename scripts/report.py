import os
from os import walk
from os import listdir
from os.path import isfile, join, basename
import re
import json
from optparse import OptionParser
import operator

class SilkReport:

	timeout = 3600 #in seconds
	apk_home = ""
	prefix = "silk_report-"
	no_first_flow = []
	apk_full_complete = []
	apk_oom = []
	apk_stackoverflow = []
	apk_timeout = []
	apk_exception = []
	apk_candidate = []
	apk_shared = []

	time_1hr = 0
	time_2hr = 0

	apk_accum_time = 0
	apk_time_counter = 0.0

	apk_accum_mem = 0.0
	apk_mem_counter = 0.0
	
	keyword_frequency = {}
	apk_no_first_flow_counter = 0
	apk_first_flow_counter = 0
	apk_no_second_flow_counter = 0

	"""
	Total apks analyzed
	"""
	apk_count = 0
	regex_jvm_oom = re.compile("^java_error")
	regex_log = re.compile("^out.*\.log$")
	regex_results = re.compile("results\.json$")
	regex_time = re.compile("\s*Elapsed \(wall clock\) time \(h\:mm\:ss or m\:ss\)\:\s([\d\:]+)")
	regex_mem_usage = re.compile("\s*Maximum resident set size \(kbytes\)\:\s+(\d+)")
	regex_stackoverflow = re.compile("StackOverflow")

	regex_exception = re.compile("java\.lang\.RuntimeException|java\.lang\.NullPointerException")




	def __init__(self, apk_home, keywords=None):
		#self.apks_file_list = apks_file_list
		self.apk_home = apk_home
		self.keywords = None
		if keywords:
			self._load_keywords(keywords)

	def _load_keywords(self, keywords):
		self.keywords = lines = [line.strip() for line in open(keywords)]

	def process_result_dir(self,dir):
		first_flow_flag = False
		second_flow_flag = False
		results = False
		isCandidate = False
		sharedModel = False
		timeout = False
		stackoverflow = False
		exception = False
		oom = False

		apk_name = basename(dir)	
		keywords_used = set()
		#Has a results file?
		onlyfiles = [ f for f in listdir(dir) if isfile(join(dir,f)) ]
		for file in onlyfiles:
			fullpath = join(dir,file)

			if self.regex_jvm_oom.search(file):
				oom = True

			if self.regex_log.match(file):
				timeout_, stackoverflow_, exception_, oom_ = self.process_log(fullpath,apk_name)
				if timeout_:
					timeout = True
				if stackoverflow_:		
					stackoverflow = True
				if exception_:
					exception = True
				if oom_:
					oom = True
				
			if self.regex_results.search(file):
				(hasResults_, isCandidate_, sharedModel_) = self.process_result_file(fullpath, apk_name, keywords_used)
				if hasResults_:
					results = True
				if isCandidate_:
					isCandidate = True
				if sharedModel_:
					sharedModel = True

			if file == "net2model.flag":
				first_flow_flag = True
			
			if file == "model2ui.flag":
				second_flow_flag = True
		#print apk_name 
		#print keywords_used
		for key in keywords_used:
			if key in self.keyword_frequency:
				self.keyword_frequency[key] = self.keyword_frequency[key] + 1
			else:
				self.keyword_frequency[key] = 1	
		if oom:
			self.apk_oom.append(apk_name)

	 	if timeout:
			self.apk_timeout.append(apk_name)

		if stackoverflow:
			self.apk_stackoverflow.append(apk_name)

		if exception and not(stackoverflow):
			self.apk_exception.append(apk_name)

		if results:
			self.apk_full_complete.append(apk_name)

		if isCandidate:
			self.apk_candidate.append(apk_name)	

		if sharedModel:
			self.apk_shared.append(apk_name)

		if first_flow_flag and not(second_flow_flag):
			""" 
			will this ever happen? if a crash occurs?
			"""
			self.apk_no_second_flow_counter = self.apk_no_second_flow_counter + 1
			

		if first_flow_flag and second_flow_flag and not(results):
			"""
			If first flow is not found, model2ui.flag set so it wont try again
			Something that is confusing (and wrong?)is that a results file is created even if the source and sinks arent found in flow 2
			
			To check for actual result, check in log for "No sources or sinks found, aborting analysis"
			Or better in results.json value of cluster
			"""
			self.apk_first_flow_counter = self.apk_first_flow_counter + 1
			

		if not(first_flow_flag) and second_flow_flag:
			"""
			This will happen if no source and sinks are found in the first flow so we dont rerun the analysis
			"""
			self.apk_no_first_flow_counter = self.apk_no_first_flow_counter + 1
			self.no_first_flow.append(apk_name)
		
		if not(second_flow_flag) and not(timeout) and not(stackoverflow):
			pass
		if exception and not(stackoverflow):
			pass
						
	def process_log(self,f,apk_name):
		#This operation requires resolving level HIERARCHY
		oom = False
		timeout = False
		stackoverflow = False
		exception = False
		with open(f) as infile:
			for line in infile:
				m = self.regex_time.match(line)
				if m:
					hhmmss = m.group(1)
					l = hhmmss.split(":")
					if len(l) == 3:
						t = int(l[0]) * 3600 + int(l[1]) * 60 + int(l[2])
					else:
						t = int(l[0]) * 60 + int(l[1])

					# give it a minute to clean up, i rmemebe rthey didnt all end right at that time
					if 3660 > t and t > 3600:
						self.time_1hr = self.time_1hr + 1
					if 7260 > t and t > 7200:
						self.time_2hr = self.time_2hr + 1
	
					if t > self.timeout:
						timeout = True
						#print apk_name
					self.apk_accum_time = self.apk_accum_time + t
					self.apk_time_counter = self.apk_time_counter + 1.0
					
					
				mem_match = self.regex_mem_usage.match(line)
				if mem_match:
					self.apk_accum_mem = self.apk_accum_mem + float(mem_match.group(1))
					self.apk_mem_counter = self.apk_mem_counter + 1.0

				if self.regex_stackoverflow.search(line):
					stackoverflow = True

				if self.regex_exception.search(line):
					exception = True
					#print line
				if "There is insufficient memory for the Java Runtime Environment to continue" in line:
					oom = True
			
		return (timeout, stackoverflow, exception, oom)

	def process_result_file(self,f, apk_name, keywords_used):
		"""
			Find candidates
			Args:
				f: json file
				apk_name: name of apk 
			Return:
				Tuple, (True if results exist, True if candidate)
		"""
		hasResults = False
		isCandidate = False
		sharedModel = False
		with open(f) as json_file:
			results = json.load(json_file)


			#This sucks really bad, not sure why not all are reported in cluster
			shown = []
			shown.append(results["usedConfidenceLow"])
			shown.append(results["usedConfidenceHigh"])
		
			if len(shown) > 0:
				hasResults = True
 	
			all = results["getMethodsInApp"].keys()
			for i in shown:
				try:
					all.remove(i)
				except:
					pass
			#Now we are left with only those that are hidden
			if self._has_keyword(all, keywords_used):
				isCandidate = True
 	
			if "cluster" in results:

				cluster = results["cluster"]	
				if len(cluster.keys()) > 0:
					hasResults = True
				
				#Lets do it all through the clusters
				model_counts = {}	
				for deserialize in cluster:
					model = cluster[deserialize]["model"]
					hidden = cluster[deserialize]["hidden"]
					#print "apk={} model={} hidden={}".format(apk_name, model, len(hidden))
					if self._has_keyword(hidden,keywords_used):
						isCandidate = True
						#print "has hidden keyword {}".format(apk_name)
					num_hidden = len(hidden)	
					if model in model_counts:
						model_counts[model].append(num_hidden)
					else:
						model_counts[model] = [num_hidden]

				#Now check and see if any use the same model and if so have different hidden 
				for model in model_counts:
					if len(set(model_counts[model])) > 1:
						isCandidate = True
						sharedModel = True


		return (hasResults, isCandidate, sharedModel)


	def _has_keyword(self, arr, keywords_used):
		"""
		See if keyword in any of the array items
		"""
		if not self.keywords:
			return False
		hasKeyword = False
		for var in arr:
		 	var = var.replace("\u003c","").replace("\u003e", "")
			name = var.split(" ")[2]	
			#Next we want to split by _ and capital letter
			words = re.sub( r"([A-Z])", r" \1", re.sub( r"_", r" ", name)).split() 
			for word in words:
				word = word.lower()
				for keyword in self.keywords:
					key = keyword.lower()
					if word.startswith(key):
						keywords_used.add(key)
						"""
						if key in self.keyword_frequency:
							self.keyword_frequency[key] = self.keyword_frequency[key] + 1
						else:
							self.keyword_frequency[key] = 1	
						"""
						hasKeyword = True
		return hasKeyword #False 
	
	def output_apk_lists(self):


		self.write_list(self.prefix + 'apks_no_first_flow.txt', self.no_first_flow)

		self.write_list(self.prefix + 'apks_exception.txt', self.apk_exception)
		self.write_list(self.prefix + 'apks_oom.txt', self.apk_oom)
		self.write_list(self.prefix + 'apks_with_results.txt', self.apk_full_complete)
		self.write_list(self.prefix + 'apks_with_stackoverflow.txt', self.apk_stackoverflow)
		self.write_list(self.prefix + 'apks_timedout.txt', self.apk_timeout)
		
		if self.keywords:
			self.write_list(self.prefix + 'apks_candidate.txt', self.apk_candidate)

	def print_keyword_frequency(self):
		sorted_x = sorted(self.keyword_frequency.items(), key=operator.itemgetter(1),reverse=True)
		#print sorted_x
		for (key, count) in sorted_x:
			print "{}\t{}".format(key,count)
			
	def write_list(self, filename, list):
		with open(filename, 'w') as f:
			for i in list:
				f.write("%s%s\n" % (self.apk_home, i))

	
	def summary(self):
		#num_lines = sum(1 for line in open(self.apks_file_list))

		print "Timeout 1hr {}".format(self.time_1hr)
		print "Timeout 2hr {}".format(self.time_2hr)
		not_complete=self.apk_count - len(self.apk_full_complete)
		unknowns = not_complete - self.apk_no_first_flow_counter - self.apk_first_flow_counter - self.apk_no_second_flow_counter - len(self.apk_stackoverflow) - len(self.apk_exception) - len(self.apk_oom) - len(self.apk_timeout)

		#print "#0 Total to analyze = {}".format(num_lines)
		print "#1 Number of analysis terminated = {}".format(self.apk_count)
		print "#2 Completed analysis (both flows) = {}".format(len(self.apk_full_complete))
		print ""
		print "Number that did not complete = {}".format(self.apk_count - len(self.apk_full_complete)) 
		print "\tNumber analysis not found first flow (clean) = {}".format(self.apk_no_first_flow_counter) 
		#print "\tAnalysis in which only first flow success (clean) = {}".format(self.apk_first_flow_counter)
		print "\tNumber that finished flow 1 but not flow 2 (something bad) = {}".format(self.apk_no_second_flow_counter) 
		
		print "\tNumber of stackoverflows = {}".format(len(self.apk_stackoverflow))
		print "\tNumber of runtime exceptions (not including stackoverflow)= {}".format(len(self.apk_exception))
		print "\tNumber of JVM oom = {}".format(len(self.apk_oom))
		print "\tNumber analysis timed out = {}".format(len(self.apk_timeout))
	#	print "Number analysis with SILK exception"
	#	print "Number of unknowns = {}".format(unknowns)
		print ""
		print "#3 Average time  = {0:.2f}m".format((self.apk_accum_time/self.apk_time_counter)/60.0)
		print "#4 Average memory usage = {0:.2f}kbytes".format(self.apk_accum_mem/self.apk_mem_counter)
		print ""
		print ""
		print "Number candidates = {}".format(len(self.apk_candidate)) 	
		print "Number candidates with shared model = {}".format(len(self.apk_shared))

		self.print_keyword_frequency()

		self.output_apk_lists()	

	def report(self, f):
		if os.path.isdir(f):
			directory =f 
			for dir in [ name for name in os.listdir(f) if os.path.isdir(os.path.join(f, name)) ]:
			#for dir in [x[0] for x in os.walk(directory)]:
				dir = os.path.join(f, dir)
				#print "dir=" + dir
				self.apk_count = self.apk_count + 1
				self.process_result_dir(dir)
		else:
			with open(f) as infile:
				for line in infile:
					self.apk_count = self.apk_count + 1
					self.process_result_dir(line.strip())
			
		self.output_apk_lists()
		self.summary()

if __name__ == "__main__":

	apk_home = ''# '/data/social/'
	apks = '/home/wfkoch/apks/playdrone-social-fromjson_final.txt'

	parser = OptionParser()
	parser.add_option('-k', '--keyword',dest="keywords", help='Keyword file')
	parser.add_option('-b', '--base', dest="base", default='', help="Directory base to add to files generated")

	(options, args) = parser.parse_args()
	result_dir = args[0]
	
	print result_dir
	reporter = SilkReport(options.base, keywords=options.keywords)
	reporter.report(result_dir)
