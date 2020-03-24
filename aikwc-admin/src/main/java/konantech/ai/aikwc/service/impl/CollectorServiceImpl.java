package konantech.ai.aikwc.service.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.provider.HibernateUtils;
import org.springframework.stereotype.Service;

import konantech.ai.aikwc.common.config.CheckStatusHandler;
import konantech.ai.aikwc.entity.Agency;
import konantech.ai.aikwc.entity.Collector;
import konantech.ai.aikwc.entity.Group;
import konantech.ai.aikwc.entity.Site;
import konantech.ai.aikwc.repository.AgencyRepository;
import konantech.ai.aikwc.repository.CollectorRepository;
import konantech.ai.aikwc.repository.GroupRepository;
import konantech.ai.aikwc.repository.SiteRepository;
import konantech.ai.aikwc.service.CollectorService;

@Service("CollectorService")
public class CollectorServiceImpl implements CollectorService {

	@Autowired
	GroupRepository groupRepository;
	
	@Autowired
	private SiteRepository siteRepository;
	
	@Autowired
	private CollectorRepository collectorRepository;
	
	@Autowired
	private AgencyRepository agencyRepository;
	
	@Autowired
	CheckStatusHandler statusHandler;
	
	public Group saveGroup(Group group) {
		return groupRepository.save(group);
	}
	public void updateGroup(Group group) {
		groupRepository.updateGroup(group.getPk(), group.getCode(), group.getName());
	}
	public void deleteGroup(Group group) {
		groupRepository.deleteById(group.getPk());
	}
	public List<Site> getSiteList(){
		return siteRepository.findAll();
	}
	public List<Site> getSiteListInAgency(int agency) {
		return siteRepository.getSiteListInAgency(agency);
	}
	
	public List<Collector> getCollectorList(){
		return collectorRepository.findAllWithJoin();
	}
	public Collector saveCollector(Collector collector){
		return collectorRepository.save(collector);
	}
	public List<Collector> getCollectorListInSite(String site){
		return collectorRepository.findBySite(site);
	}
	public Collector getCollectorInfo(int pk) {
		return collectorRepository.findById(pk).get();
	}
	public List<Collector> getCollectorListInSiteInUse(String site){
		return collectorRepository.findAllInSite(site);
	}
	public List<Collector> getCollectorListInAgency(int agency){
		return collectorRepository.findInAgency(agency);
	}
	
	public void saveCollectorDetail(Collector collector){
		Optional<Collector> op = collectorRepository.findById(collector.getPk());
		
		op.ifPresent(newer -> {
			newer.setStartUrl(collector.getStartUrl());
			newer.setTitleLink(collector.getTitleLink());
			newer.setTitle(collector.getTitle());
			newer.setContent(collector.getContent());
			newer.setWriter(collector.getWriter());
			newer.setWdatePattern(collector.getWdatePattern());
			newer.setWriteDate(collector.getWriteDate());
			newer.setPageUrl(collector.getPageUrl());
			newer.setContId(collector.getContId());
			collectorRepository.save(newer);
		});
		
	}
	
	public void updateStatus(int pk, String status) {
		Optional<Collector> op = collectorRepository.findById(pk);
		op.ifPresent(newer -> {
			newer.setStatus(status);
			collectorRepository.saveAndFlush(newer);
		});
		
		try {
			statusHandler.sendCollectorStatus();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String getAgencyNameForCollector(String pk) {
		return agencyRepository.findOneByPk(pk).getName();
	}
	
	public List<Site> getSiteInGroup(String group){
		return siteRepository.findByGrp(group);
	}
	public Site saveSite(Site site) {
		return siteRepository.save(site);
	}
}