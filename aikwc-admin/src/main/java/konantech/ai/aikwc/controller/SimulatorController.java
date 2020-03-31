package konantech.ai.aikwc.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import konantech.ai.aikwc.common.config.AsyncConfig;
import konantech.ai.aikwc.common.config.StatusWebSocketHandler;
import konantech.ai.aikwc.entity.Agency;
import konantech.ai.aikwc.entity.Collector;
import konantech.ai.aikwc.entity.KLog;
import konantech.ai.aikwc.entity.KTask;
import konantech.ai.aikwc.service.CollectorService;
import konantech.ai.aikwc.service.CommonService;
import konantech.ai.aikwc.service.CrawlService;
import konantech.ai.aikwc.service.ScheduleService;
import konantech.ai.aikwc.service.TaskService;

@Controller
@RequestMapping("simulator")
public class SimulatorController {
	@Resource
	CommonService commonService;
	
	@Resource
	CollectorService collectorService;
	@Autowired
	CrawlService crawlService;
	@Autowired
	AsyncConfig asyncConfig;
	@Autowired
	StatusWebSocketHandler statusHandler;
	
	@Autowired
	ScheduleService scheduleService;
	@Autowired
	TaskService taskService;
	
	@RequestMapping("/list")
	public String list(@RequestParam(name = "agencyNo", required = false, defaultValue = "0") Integer agencyNo
			,Model model) {
		Map map = commonService.commInfo(agencyNo);
		Agency selAgency = (Agency) map.get("selAgency");
		model.addAttribute("selAgency", selAgency);
		model.addAttribute("agencyList", map.get("agencyList"));
		model.addAttribute("groupList", map.get("groupList"));
		model.addAttribute("agencyNo", selAgency.getPk());
		model.addAttribute("menuNo", "1");
		
		return "sml/runJob";
	}
	
	@RequestMapping("/schedule")
	public String runSchedule(@RequestParam(name = "agencyNo", required = false, defaultValue = "0") Integer agencyNo
			,@RequestParam(name = "menuNo", required = false, defaultValue = "1") String menuNo
			,Model model) {
		Map map = commonService.commInfo(agencyNo);
		Agency selAgency = (Agency) map.get("selAgency");
		model.addAttribute("selAgency", selAgency);
		model.addAttribute("agencyList", map.get("agencyList"));
		model.addAttribute("groupList", map.get("groupList"));
		model.addAttribute("agencyNo", selAgency.getPk());
		model.addAttribute("menuNo", "2");
		
		List<Map<String, String>> list = taskService.getAllTaskWithCollectorName();
		model.addAttribute("taskList", list);
		
		model.addAttribute("taskCnt", list.size());
		model.addAttribute("taskRsvCnt", scheduleService.getSchedulingTaskCount());
		model.addAttribute("taskRunCnt", scheduleService.getTaskCount());
		
		return "sml/runSchedule";
	}
	@RequestMapping("/log")
	public String viewLog(@RequestParam(name = "agencyNo", required = false, defaultValue = "0") Integer agencyNo
			,@RequestParam(name = "menuNo", required = false, defaultValue = "1") String menuNo
			,Model model) {
		
		Map map = commonService.commInfo(agencyNo);
		Agency selAgency = (Agency) map.get("selAgency");
		model.addAttribute("selAgency", selAgency);
		model.addAttribute("agencyList", map.get("agencyList"));
		model.addAttribute("groupList", map.get("groupList"));
		model.addAttribute("agencyNo", selAgency.getPk());
		model.addAttribute("menuNo", "3");
		
		List<KLog> logList = new ArrayList<KLog>();
		logList = commonService.getAgencyLogList(selAgency.getStrPk());
		model.addAttribute("logList", logList);
		return "sml/viewLog";
	}
	
	
	
	@RequestMapping(value ="/collectorList", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Object> collectorList(@RequestBody Map<String,String> params) {
		
		String agency = params.get("agencyNo");
		List<Collector> result = new ArrayList<Collector>();
		if(agency != null && !agency.equals("")) {
			result = collectorService.getCollectorListInAgency(Integer.parseInt(agency));
		}else
			result = collectorService.getCollectorList();
			
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("result", result);
		map.put("taskCnt", asyncConfig.getTaskCount());
		
		return map;
	}
	
	@RequestMapping("/crawl")
	@ResponseBody
	public void getCrawl(@RequestBody Collector collector) throws Exception {
		
		Collector selectedCollector = collectorService.getCollectorInfo(collector.getPk());
		selectedCollector.setStartPage(collector.getStartPage());
		selectedCollector.setEndPage(collector.getEndPage());
		
//		Agency Agency = collectorService.getAgencyNameForCollector(selectedCollector.getToSite().getGroup().getAgency());
//		String agencyName = Agency.getName();
//		selectedCollector.getToSite().getGroup().setAgencyName(agencyName);
//		selectedCollector.setChannel("기관");
		preworkForCrawling(selectedCollector);
		
		//1. update Running status / send websocket message
		collectorService.updateStatus(collector.getPk(), "R");
		
		CompletableFuture cf = crawlService.webCrawlThread(selectedCollector);
		statusHandler.sendTaskCnt(asyncConfig.getTaskCount());
		
		CompletableFuture<Void> after = cf.handle((res,ex) -> {
			statusHandler.sendTaskCnt(asyncConfig.getAfterTaskCount());
			return null;
		});
		
	}
	
	@RequestMapping(value="/schedule/save", method = RequestMethod.POST)
	public String saveSchdule(@ModelAttribute KTask task) throws Exception {
//		KTask task = new KTask();
//		List<KTask> list = taskService.getTaskByCollector(task.getCollector());
		Long count = taskService.getTaskByCollectorCount(task.getCollector());
		String taskNo = "C"+task.getCollector()+"-"+(count+1);
		task.setTaskNo(taskNo); // C8-1
		Collector collector = collectorService.getCollectorInfo(Integer.parseInt(task.getCollector()));
		collector.setStartPage(task.getStart());
		collector.setEndPage(task.getEnd());
		preworkForCrawling(collector);
		scheduleService.registerSchedule(task,collector);
		taskService.saveTask(task);
		return "redirect:/simulator/schedule";
	}
	
	@RequestMapping(value="/schedule/delete", method = RequestMethod.POST)
	public String deleteSchdule(@ModelAttribute(name = "pk") String pk) throws Exception {
		KTask task = taskService.getTaskByPk(pk);
		scheduleService.stopSchedule(task);
		taskService.deleteTask(task);
		return "redirect:/simulator/schedule";
	}
	
	public void preworkForCrawling(Collector selectedCollector) {
		Agency Agency = collectorService.getAgencyNameForCollector(selectedCollector.getToSite().getGroup().getAgency());
		String agencyName = Agency.getName();
		selectedCollector.getToSite().getGroup().setAgencyName(agencyName);
		selectedCollector.setChannel("기관");
	}
}

