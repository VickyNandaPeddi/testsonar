package com.aashdit.digiverifier.config.superadmin.service;

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import com.aashdit.digiverifier.common.ContentRepository;
import com.aashdit.digiverifier.common.enums.ContentCategory;
import com.aashdit.digiverifier.common.enums.ContentSubCategory;
import com.aashdit.digiverifier.common.enums.ContentType;
import com.aashdit.digiverifier.common.enums.FileType;
import com.aashdit.digiverifier.common.model.Content;
import com.aashdit.digiverifier.config.candidate.Enum.CandidateStatusEnum;
import com.aashdit.digiverifier.config.candidate.Enum.IDtype;
import com.aashdit.digiverifier.config.candidate.dto.*;
import com.aashdit.digiverifier.config.candidate.model.*;
import com.aashdit.digiverifier.config.candidate.repository.*;
import com.aashdit.digiverifier.config.superadmin.Enum.ExecutiveName;
import com.aashdit.digiverifier.config.superadmin.Enum.ReportType;
import com.aashdit.digiverifier.config.candidate.service.CandidateService;
import com.aashdit.digiverifier.config.superadmin.Enum.SourceEnum;
import com.aashdit.digiverifier.config.superadmin.Enum.VerificationStatus;
import com.aashdit.digiverifier.config.superadmin.model.Organization;
import com.aashdit.digiverifier.config.superadmin.model.OrganizationExecutive;
import com.aashdit.digiverifier.config.superadmin.model.ToleranceConfig;
import com.aashdit.digiverifier.email.dto.Email;
import com.aashdit.digiverifier.email.dto.EmailProperties;
import com.aashdit.digiverifier.epfo.model.CandidateEPFOResponse;
import com.aashdit.digiverifier.epfo.repository.CandidateEPFOResponseRepository;
import com.aashdit.digiverifier.epfo.repository.EpfoDataRepository;
import com.aashdit.digiverifier.epfo.model.EpfoData;
import com.aashdit.digiverifier.itr.model.CanditateItrResponse;
import com.aashdit.digiverifier.itr.model.ITRData;
import com.aashdit.digiverifier.itr.repository.CanditateItrEpfoResponseRepository;
import com.aashdit.digiverifier.itr.repository.ITRDataRepository;
import com.aashdit.digiverifier.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.aashdit.digiverifier.common.model.ServiceOutcome;
import com.aashdit.digiverifier.config.admin.dto.VendorUploadChecksDto;
import com.aashdit.digiverifier.config.admin.model.User;
import com.aashdit.digiverifier.config.admin.model.VendorChecks;
import com.aashdit.digiverifier.config.admin.model.VendorUploadChecks;
import com.aashdit.digiverifier.config.admin.repository.UserRepository;
import com.aashdit.digiverifier.config.admin.repository.VendorChecksRepository;
import com.aashdit.digiverifier.config.admin.repository.VendorUploadChecksRepository;
import com.aashdit.digiverifier.config.superadmin.dto.CandidateDetailsForReport;
import com.aashdit.digiverifier.config.superadmin.dto.OrganizationDto;
import com.aashdit.digiverifier.config.superadmin.dto.ReportResponseDto;
import com.aashdit.digiverifier.config.superadmin.dto.ReportSearchDto;
import com.aashdit.digiverifier.config.superadmin.repository.ColorRepository;
import com.aashdit.digiverifier.config.superadmin.repository.OrganizationRepository;
import com.aashdit.digiverifier.config.candidate.repository.CandidateAddCommentRepository;

import lombok.extern.slf4j.Slf4j;

import static com.aashdit.digiverifier.digilocker.service.DigilockerServiceImpl.DIGIVERIFIER_DOC_BUCKET_NAME;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    public static final String NO_DATA_FOUND = "NO DATA FOUND";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CandidateStatusRepository candidateStatusRepository;

    @Autowired
    private CandidateEmailStatusRepository candidateEmailStatusRepository;

    @Autowired
    private ColorRepository colorRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private CandidateCafExperienceRepository candidateCafExperienceRepository;

    @Autowired
    private CandidateIdItemsRepository candidateIdItemsRepository;

    @Autowired
    private CandidateCafAddressRepository candidateCafAddressRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationServiceImpl organizationServiceImpl;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private CandidateService candidateService;

    @Autowired
    private CandidateStatusHistoryRepository candidateStatusHistoryRepository;

    @Autowired
    private AwsUtils awsUtils;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private EpfoDataRepository epfoDataRepository;

    @Autowired
    private ITRDataRepository itrDataRepository;

    @Autowired
    private CanditateItrEpfoResponseRepository canditateItrEpfoResponseRepository;

    @Autowired
    @Lazy
    private CandidateEPFOResponseRepository candidateEPFOResponseRepository;


    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    @Lazy
    private EmailProperties emailProperties;

    @Autowired
    @Lazy
    private EmailSentTask emailSentTask;

    @Autowired
    private CandidateAddCommentRepository candidateAddCommentRepository;

    @Autowired
    private VendorChecksRepository vendorChecksRepository;

    @Autowired
    private VendorUploadChecksRepository vendorUploadChecksRepository;


    SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private static final String emailContent = "<html>\n"
            + "<head>\n"
            + "</head>\n"
            + "<body>\n"
            + "<p style=\"font-size:12px\">Dear %s, <br><br>Greetings from Team-DigiVerifier <br><br>Please find attached %s report for %s. </p>\n"
            + "<p style=\"font-size:8px\">\n"
            + "DISCLAIMER:\n"
            + "The information contained in this e-mail message and/or attachments to it may contain confidential or privileged information. If you are not the intended recipient, any dissemination, use, review, distribution, printing or copying of the information contained in this e-mail message and/or attachments to it are strictly prohibited. If you have received this communication in error, please notify us by reply e-mail or telephone and immediately and permanently delete the message and any attachments. Thank you.</p>\n"
            + "<p style=\"font-size:12px\">Regards, <br> Team-DigiVerifier </p>\n"
            + "\n"
            + "</body>\n"
            + "</html>";


    // @Override
    // public ServiceOutcome<ReportSearchDto> vendorReport(ReportSearchDto reportSearchDto) {
    // 	ServiceOutcome<ReportSearchDto> svcSearchResult = new ServiceOutcome<ReportSearchDto>();
    // 	System.out.println(reportSearchDto);
    // 	String strToDate="";
    // 	String strFromDate="";
    // 	try {
    // 		strToDate=reportSearchDto.getToDate();
    // 		strFromDate=reportSearchDto.getFromDate();
    // 		Date startDate = format.parse(strFromDate+" 00:00:00");
    // 		Date endDate = format.parse(strToDate+" 23:59:59");
    // 	}catch(Exception ex) {
    // 	log.error("Exception occured in vendorReport method in ReportServiceImpl-->",ex);
    // 	svcSearchResult.setData(null);
    // 	svcSearchResult.setOutcome(false);
    // 	svcSearchResult.setMessage("Something Went Wrong, Please Try After Sometimes.");
    // 	}
    // return svcSearchResult;

    // }


    @Override
    public ServiceOutcome<ReportSearchDto> getCustomerUtilizationReportData(ReportSearchDto reportSearchDto) {
        ServiceOutcome<ReportSearchDto> svcSearchResult = new ServiceOutcome<ReportSearchDto>();
        List<Object[]> resultList = null;
        User user = SecurityHelper.getCurrentUser();
        String strToDate = "";
        String strFromDate = "";
        List<Long> orgIds = new ArrayList<Long>();
        ReportSearchDto reportSearchDtoObj = null;
        try {
            if (reportSearchDto == null) {
                strToDate = ApplicationDateUtils.getStringTodayAsDDMMYYYY();
                strFromDate = ApplicationDateUtils.subtractNoOfDaysFromDateAsDDMMYYYY(new SimpleDateFormat("dd/MM/yyyy").parse(strToDate), 30);
                if (user.getRole().getRoleCode().equals("ROLE_CBADMIN")) {
                    orgIds.add(0, 0l);
                } else {
                    Long orgIdLong = user.getOrganization().getOrganizationId();
                    orgIds.add(orgIdLong);
                    reportSearchDto = new ReportSearchDto();
                    reportSearchDto.setOrganizationIds(orgIds);
                }

            } else {
                strToDate = reportSearchDto.getToDate();
                strFromDate = reportSearchDto.getFromDate();
                orgIds.addAll(reportSearchDto.getOrganizationIds());
            }
            Date startDate = format.parse(strFromDate + " 00:00:00");
            Date endDate = format.parse(strToDate + " 23:59:59");
            StringBuilder query = new StringBuilder();
            if (reportSearchDto != null && reportSearchDto.getOrganizationIds() != null && reportSearchDto.getOrganizationIds().size() > 0 && reportSearchDto.getOrganizationIds().get(0) != 0l) {
                query.append("SELECT t1.orgid,t1.orgname,t1.newuploadcount,t1.reinvitecount,t1.finalreportDeliveredcount,t1.intereimreportDeliveredcount,\n");
                query.append("t2.pendingcount,t1.processDeclinecount,t1.invitationExpiredcount,t1.agentCount\n");
                query.append("FROM\n");
                query.append("(SELECT sm.organization_id AS orgid,organization.organization_name AS orgname,  \n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='NEWUPLOAD' OR statusMaster.status_code ='INVALIDUPLOAD') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS newuploadcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='REINVITE') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS reinvitecount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='FINALREPORT') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS finalreportDeliveredcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='PENDINGAPPROVAL') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS intereimreportDeliveredcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='PROCESSDECLINED') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS processDeclinecount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='INVITATIONEXPIRED') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS invitationExpiredcount,\n");
                if (user.getRole().getRoleCode().equals("ROLE_AGENTSUPERVISOR")) {
                    query.append("COUNT( DISTINCT CASE WHEN roleMaster.role_code ='ROLE_AGENTHR' AND userMaster.is_user_active =TRUE AND userMaster.user_id IN(?4) THEN userMaster.user_id END) AS agentCount \n");
                } else if (user.getRole().getRoleCode().equals("ROLE_AGENTHR")) {
                    query.append("0 AS agentCount \n");
                } else {
                    query.append("COUNT( DISTINCT CASE WHEN roleMaster.role_code ='ROLE_AGENTHR' AND userMaster.is_user_active =TRUE THEN userMaster.user_id END) AS agentCount \n");
                }
                query.append("FROM   t_dgv_service_master sm \n");
                query.append("LEFT JOIN t_dgv_organization_master organization ON organization.organization_id = sm.organization_id \n");
                query.append("LEFT JOIN t_dgv_candidate_basic candidatebasic ON candidatebasic.organization_id=organization.organization_id \n");
                query.append("LEFT JOIN t_dgv_candidate_status_history candidatestatushistory ON candidatestatushistory.candidate_id=candidatebasic.candidate_id \n");
                query.append("LEFT JOIN t_dgv_status_master statusMaster ON statusMaster.status_master_id=candidatestatushistory.status_master_id \n");
                query.append("LEFT JOIN t_dgv_user_master userMaster ON userMaster.orgainzation_id=organization.organization_id \n");
                query.append("LEFT JOIN t_dgv_role_master roleMaster ON roleMaster.role_id =userMaster.role_id  \n");
                query.append("WHERE organization.is_active =TRUE \n");
                if (user.getRole().getRoleCode().equals("ROLE_AGENTSUPERVISOR") || user.getRole().getRoleCode().equals("ROLE_AGENTHR")) {
                    query.append("AND candidatebasic.created_by IN (?4) \n");
                }
                query.append("AND organization.organization_id IN(?3)\n");
                query.append("GROUP BY organization.organization_name,sm.organization_id ORDER BY organization.organization_name ASC) t1\n");
                query.append("LEFT JOIN\n");
                query.append("(SELECT sm.organization_id AS orgid,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='INVITATIONSENT' OR statusMaster.status_code='ITR' OR statusMaster.status_code='EPFO' OR statusMaster.status_code='DIGILOCKER' OR statusMaster.status_code='RELATIVEADDRESS') AND candidatestatus.last_updated_on BETWEEN ?1 AND ?2 THEN candidatestatus.candidate_id END) AS pendingcount\n");
                query.append("FROM   t_dgv_service_master sm \n");
                query.append("LEFT JOIN t_dgv_organization_master organization ON organization.organization_id = sm.organization_id \n");
                query.append("LEFT JOIN t_dgv_candidate_basic candidatebasic ON candidatebasic.organization_id=organization.organization_id \n");
                query.append("LEFT JOIN t_dgv_candidate_status candidatestatus ON candidatestatus.candidate_id=candidatebasic.candidate_id\n");
                query.append("LEFT JOIN t_dgv_status_master statusMaster ON statusMaster.status_master_id=candidatestatus.status_master_id \n");
                query.append("LEFT JOIN t_dgv_user_master userMaster ON userMaster.orgainzation_id=organization.organization_id \n");
                query.append("LEFT JOIN t_dgv_role_master roleMaster ON roleMaster.role_id =userMaster.role_id  \n");
                query.append("WHERE organization.is_active =TRUE \n");
                if (user.getRole().getRoleCode().equals("ROLE_AGENTSUPERVISOR") || user.getRole().getRoleCode().equals("ROLE_AGENTHR")) {
                    query.append("AND candidatebasic.created_by IN (?4) \n");
                }
                query.append("AND organization.organization_id IN(?3)\n");
                query.append("GROUP BY sm.organization_id ORDER BY organization.organization_name ASC) t2\n");
                query.append("ON t1.orgid=t2.orgid  \n");


                Query squery = entityManager.createNativeQuery(String.valueOf(query));
                squery.setParameter(1, startDate);
                squery.setParameter(2, endDate);
                squery.setParameter(3, reportSearchDto.getOrganizationIds());
                if (user.getRole().getRoleCode().equals("ROLE_AGENTSUPERVISOR")) {
                    List<User> agentList = userRepository.findAllByAgentSupervisorUserId(user.getUserId());
                    if (!agentList.isEmpty()) {
                        List<Long> agentIdsList = agentList.parallelStream().map(x -> x.getUserId()).collect(Collectors.toList());
                        agentIdsList.add(user.getUserId());
                        reportSearchDto.setAgentIds(agentIdsList);
                        squery.setParameter(4, reportSearchDto.getAgentIds());
                    }
                }
                if (user.getRole().getRoleCode().equals("ROLE_AGENTHR")) {
                    List<Long> agentIdsList = new ArrayList<>();
                    agentIdsList.add(user.getUserId());
                    reportSearchDto.setAgentIds(agentIdsList);
                    squery.setParameter(4, reportSearchDto.getAgentIds());
                }

                resultList = squery.getResultList();
            } else {
                query.append("SELECT t1.orgid,t1.orgname,t1.newuploadcount,t1.reinvitecount,t1.finalreportDeliveredcount,t1.intereimreportDeliveredcount,\n");
                query.append("t2.pendingcount,t1.processDeclinecount,t1.invitationExpiredcount,t1.agentCount\n");
                query.append("FROM\n");
                query.append("(SELECT sm.organization_id AS orgid,organization.organization_name AS orgname,  \n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='NEWUPLOAD' OR statusMaster.status_code ='INVALIDUPLOAD') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS newuploadcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='REINVITE') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS reinvitecount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='FINALREPORT') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS finalreportDeliveredcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='PENDINGAPPROVAL') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS intereimreportDeliveredcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='PROCESSDECLINED') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS processDeclinecount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='INVITATIONEXPIRED') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS invitationExpiredcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN roleMaster.role_code ='ROLE_AGENTHR' AND userMaster.is_user_active =TRUE THEN userMaster.user_id END) AS agentCount \n");
                query.append("FROM   t_dgv_service_master sm \n");
                query.append("LEFT JOIN t_dgv_organization_master organization ON organization.organization_id = sm.organization_id \n");
                query.append("LEFT JOIN t_dgv_candidate_basic candidatebasic ON candidatebasic.organization_id=organization.organization_id \n");
                query.append("LEFT JOIN t_dgv_candidate_status_history candidatestatushistory ON candidatestatushistory.candidate_id=candidatebasic.candidate_id \n");
                query.append("LEFT JOIN t_dgv_status_master statusMaster ON statusMaster.status_master_id=candidatestatushistory.status_master_id \n");
                query.append("LEFT JOIN t_dgv_user_master userMaster ON userMaster.orgainzation_id=organization.organization_id \n");
                query.append("LEFT JOIN t_dgv_role_master roleMaster ON roleMaster.role_id =userMaster.role_id  \n");
                query.append("WHERE organization.is_active =TRUE \n");
                query.append("GROUP BY organization.organization_name,sm.organization_id ORDER BY organization.organization_name ASC) t1\n");
                query.append("LEFT JOIN\n");
                query.append("(SELECT sm.organization_id AS orgid,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='INVITATIONSENT' OR statusMaster.status_code='ITR' OR statusMaster.status_code='EPFO' OR statusMaster.status_code='DIGILOCKER' OR statusMaster.status_code='RELATIVEADDRESS') AND candidatestatus.last_updated_on BETWEEN ?1 AND ?2 THEN candidatestatus.candidate_id END) AS pendingcount\n");
                query.append("FROM   t_dgv_service_master sm \n");
                query.append("LEFT JOIN t_dgv_organization_master organization ON organization.organization_id = sm.organization_id \n");
                query.append("LEFT JOIN t_dgv_candidate_basic candidatebasic ON candidatebasic.organization_id=organization.organization_id \n");
                query.append("LEFT JOIN t_dgv_candidate_status candidatestatus ON candidatestatus.candidate_id=candidatebasic.candidate_id\n");
                query.append("LEFT JOIN t_dgv_status_master statusMaster ON statusMaster.status_master_id=candidatestatus.status_master_id \n");
                query.append("LEFT JOIN t_dgv_user_master userMaster ON userMaster.orgainzation_id=organization.organization_id \n");
                query.append("LEFT JOIN t_dgv_role_master roleMaster ON roleMaster.role_id =userMaster.role_id  \n");
                query.append("WHERE organization.is_active =TRUE \n");
                query.append("GROUP BY sm.organization_id ORDER BY organization.organization_name ASC) t2\n");
                query.append("ON t1.orgid=t2.orgid  \n");
                Query squery = entityManager.createNativeQuery(String.valueOf(query));
                squery.setParameter(1, startDate);
                squery.setParameter(2, endDate);
                resultList = squery.getResultList();
            }
            if (resultList != null && resultList.size() > 0) {
                List<ReportResponseDto> pwdvMprReportDtoList = new ArrayList<ReportResponseDto>();
                for (Object[] result : resultList) {
                    ReportResponseDto reportResponseDto = new ReportResponseDto(
                            Long.valueOf(String.valueOf(result[0])), String.valueOf(result[1]),
                            Integer.valueOf(String.valueOf(result[2])), "NEWUPLOAD",
                            Integer.valueOf(String.valueOf(result[3])), "REINVITE",
                            Integer.valueOf(String.valueOf(result[4])), "FINALREPORT",
                            Integer.valueOf(String.valueOf(result[5])), "PENDINGAPPROVAL",
                            Integer.valueOf(String.valueOf(result[6])), "PENDINGNOW",
                            Integer.valueOf(String.valueOf(result[7])), "PROCESSDECLINED",
                            Integer.valueOf(String.valueOf(result[8])), "INVITATIONEXPIRED",
                            Integer.valueOf(String.valueOf(result[9])));
                    pwdvMprReportDtoList.add(reportResponseDto);
                }
                ReportResponseDto reportResponseDtoTotal = new ReportResponseDto
                        (0l, "TOTAL",
                                pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getNewuploadcount()).sum(), "NEWUPLOADTOTAL",
                                pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getReinvitecount()).sum(), "REINVITETOTAL",
                                pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getFinalreportCount()).sum(), "FINALREPORTTOTAL",
                                pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getInterimReportCount()).sum(), "PENDINGAPPROVALTOTAL",
                                pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getPendingCount()).sum(), "PENDINGNOWTOTAL",
                                pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getProcessDeclinedCount()).sum(), "PROCESSDECLINEDTOTAL",
                                pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getInvitationExpireCount()).sum(), "INVITATIONEXPIREDTOTAL",
                                pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getAgentCount()).sum());
                pwdvMprReportDtoList.add(reportResponseDtoTotal);
                reportSearchDtoObj = new ReportSearchDto(strFromDate, strToDate, orgIds, pwdvMprReportDtoList, null);
                svcSearchResult.setData(reportSearchDtoObj);
                svcSearchResult.setOutcome(true);
                svcSearchResult.setMessage("Customer Utilization Report Data generated...");
            } else {
                reportSearchDtoObj = new ReportSearchDto(strFromDate, strToDate, orgIds, null, null);
                svcSearchResult.setData(reportSearchDtoObj);
                svcSearchResult.setOutcome(false);
                svcSearchResult.setMessage("NO RECORD FOUND");
            }
        } catch (Exception ex) {
            log.error("Exception occured in getCustomerUtilizationReportData method in ReportServiceImpl-->", ex);
            svcSearchResult.setData(null);
            svcSearchResult.setOutcome(false);
            svcSearchResult.setMessage("Something Went Wrong, Please Try After Sometimes.");
        }
        return svcSearchResult;
    }

    @Override
    public ServiceOutcome<ReportSearchDto> getCustomerUtilizationReportByAgent(ReportSearchDto reportSearchDto) {
        ServiceOutcome<ReportSearchDto> svcSearchResult = new ServiceOutcome<ReportSearchDto>();
        List<Object[]> resultList = null;
        ReportSearchDto reportSearchDtoObj = null;
        try {
            if (reportSearchDto != null) {
                Date startDate = format.parse(reportSearchDto.getFromDate() + " 00:00:00");
                Date endDate = format.parse(reportSearchDto.getToDate() + " 23:59:59");
                StringBuilder query = new StringBuilder();
                query.append("select userMaster.user_id ,userMaster.user_first_name , COALESCE(userMaster.user_last_name,'') as lastname , ");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='NEWUPLOAD' OR statusMaster.status_code ='INVALIDUPLOAD') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS newuploadcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='REINVITE') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS reinvitecount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='FINALREPORT') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS finalreportDeliveredcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='PENDINGAPPROVAL') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS intereimreportDeliveredcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='INVITATIONSENT' OR statusMaster.status_code='ITR' OR statusMaster.status_code='EPFO' OR statusMaster.status_code='DIGILOCKER' OR statusMaster.status_code='RELATIVEADDRESS') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS pendingcount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='PROCESSDECLINED') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS processDeclinecount,\n");
                query.append("COUNT( DISTINCT CASE WHEN (statusMaster.status_code ='INVITATIONEXPIRED') AND candidatestatushistory.candidate_status_change_timestamp BETWEEN ?1 AND ?2 THEN candidatestatushistory.candidate_id END) AS invitationExpiredcount\n");
                query.append("from  t_dgv_user_master userMaster ");
                query.append("left JOIN t_dgv_candidate_basic candidatebasic ON candidatebasic.created_by =userMaster.user_id  ");
                query.append("left JOIN t_dgv_candidate_status_history candidatestatushistory ON candidatestatushistory.candidate_id=candidatebasic.candidate_id ");
                query.append("left JOIN t_dgv_status_master statusMaster ON statusMaster.status_master_id=candidatestatushistory.status_master_id ");
                query.append("left JOIN t_dgv_role_master roleMaster ON roleMaster.role_id =userMaster.role_id  ");
                if (reportSearchDto.getAgentIds() != null && reportSearchDto.getAgentIds().size() > 0 && reportSearchDto.getAgentIds().get(0) != 0l) {
                    query.append("where userMaster.orgainzation_id in (?3) and userMaster.is_user_active = true and roleMaster.role_code ='ROLE_AGENTHR' and userMaster.user_id in (?4) ");
                    query.append("group by userMaster.user_id order by userMaster.user_first_name ASC; ");
                    Query squery = entityManager.createNativeQuery(String.valueOf(query));
                    squery.setParameter(1, startDate);
                    squery.setParameter(2, endDate);
                    squery.setParameter(3, reportSearchDto.getOrganizationIds());
                    squery.setParameter(4, reportSearchDto.getAgentIds());
                    resultList = squery.getResultList();
                } else {
                    query.append("where userMaster.orgainzation_id in (?3) and userMaster.is_user_active = true and roleMaster.role_code ='ROLE_AGENTHR' ");
                    query.append("group by userMaster.user_id order by userMaster.user_first_name ASC; ");
                    Query squery = entityManager.createNativeQuery(String.valueOf(query));
                    squery.setParameter(1, startDate);
                    squery.setParameter(2, endDate);
                    squery.setParameter(3, reportSearchDto.getOrganizationIds());
                    resultList = squery.getResultList();
                }
                if (resultList != null && resultList.size() > 0) {
                    List<ReportResponseDto> pwdvMprReportDtoList = new ArrayList<ReportResponseDto>();
                    for (Object[] result : resultList) {

                        ReportResponseDto reportResponseDto = new ReportResponseDto(
                                Long.valueOf(String.valueOf(result[0])), String.valueOf(result[1]) + " " + String.valueOf(result[2]),
                                Integer.valueOf(String.valueOf(result[3])), "NEWUPLOAD",
                                Integer.valueOf(String.valueOf(result[4])), "REINVITE",
                                Integer.valueOf(String.valueOf(result[5])), "FINALREPORT",
                                Integer.valueOf(String.valueOf(result[6])), "PENDINGAPPROVAL",
                                Integer.valueOf(String.valueOf(result[7])), "PENDINGNOW",
                                Integer.valueOf(String.valueOf(result[8])), "PROCESSDECLINED",
                                Integer.valueOf(String.valueOf(result[9])), "INVITATIONEXPIRED", 0);
                        pwdvMprReportDtoList.add(reportResponseDto);
                    }
                    ReportResponseDto reportResponseDtoTotal = new ReportResponseDto
                            (0l, "TOTAL",
                                    pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getNewuploadcount()).sum(), "NEWUPLOADTOTAL",
                                    pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getReinvitecount()).sum(), "REINVITETOTAL",
                                    pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getFinalreportCount()).sum(), "FINALREPORTTOTAL",
                                    pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getInterimReportCount()).sum(), "PENDINGAPPROVALTOTAL",
                                    pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getPendingCount()).sum(), "PENDINGNOWTOTAL",
                                    pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getProcessDeclinedCount()).sum(), "PROCESSDECLINEDTOTAL",
                                    pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getInvitationExpireCount()).sum(), "INVITATIONEXPIREDTOTAL",
                                    pwdvMprReportDtoList.stream().mapToInt(pojo -> pojo.getAgentCount()).sum());
                    pwdvMprReportDtoList.add(reportResponseDtoTotal);
                    reportSearchDtoObj = new ReportSearchDto(reportSearchDto.getFromDate(), reportSearchDto.getToDate(), reportSearchDto.getOrganizationIds(), pwdvMprReportDtoList, reportSearchDto.getAgentIds());
                    svcSearchResult.setData(reportSearchDtoObj);
                    svcSearchResult.setOutcome(true);
                    svcSearchResult.setMessage("Customer Utilization Report Data By Agent generated...");
                } else {
                    reportSearchDtoObj = new ReportSearchDto(reportSearchDto.getFromDate(), reportSearchDto.getToDate(), reportSearchDto.getOrganizationIds(), null, reportSearchDto.getAgentIds());
                    svcSearchResult.setData(reportSearchDtoObj);
                    svcSearchResult.setOutcome(false);
                    svcSearchResult.setMessage("NO RECORD FOUND");
                }
            }
        } catch (Exception ex) {
            log.error("Exception occured in getCustomerUtilizationReportByAgent method in ReportServiceImpl-->", ex);
            svcSearchResult.setData(null);
            svcSearchResult.setOutcome(false);
            svcSearchResult.setMessage("Something Went Wrong, Please Try After Sometimes.");
        }
        return svcSearchResult;
    }

    @Override
    public ServiceOutcome<ReportSearchDto> getCanididateDetailsByStatus(ReportSearchDto reportSearchDto) {
        ServiceOutcome<ReportSearchDto> svcSearchResult = new ServiceOutcome<ReportSearchDto>();
        List<CandidateDetailsForReport> candidateDetailsDtoList = new ArrayList<CandidateDetailsForReport>();
        CandidateEmailStatus candidateEmailStatus = null;
        List<CandidateStatus> candidateStatusList = null;
        CandidateDetailsForReport candidateDto = null;
        List<Object[]> resultList = null;
        StringBuilder query = null;
        Query squery = null;
        try {
            if (StringUtils.isNotBlank(reportSearchDto.getStatusCode()) && StringUtils.isNotBlank(reportSearchDto.getFromDate()) && StringUtils.isNotBlank(reportSearchDto.getToDate())
                    && reportSearchDto.getOrganizationIds() != null && !reportSearchDto.getOrganizationIds().isEmpty()) {
                Date startDate = format.parse(reportSearchDto.getFromDate() + " 00:00:00");
                Date endDate = format.parse(reportSearchDto.getToDate() + " 23:59:59");
                List<String> statusList = null;
                if (reportSearchDto.getStatusCode().equals("NEWUPLOAD")) {
                    if (reportSearchDto.getAgentIds() != null && !reportSearchDto.getAgentIds().isEmpty()) {
                        candidateStatusList = candidateStatusRepository.findAllByCreatedOnBetweenAndCandidateOrganizationOrganizationIdInAndCreatedByUserIdIn(startDate, endDate, reportSearchDto.getOrganizationIds(), reportSearchDto.getAgentIds());
                    } else {
                        candidateStatusList = candidateStatusRepository.findAllByCreatedOnBetweenAndCandidateOrganizationOrganizationIdIn(startDate, endDate, reportSearchDto.getOrganizationIds());
                    }
                    if (!candidateStatusList.isEmpty()) {
                        for (CandidateStatus candidateStatus : candidateStatusList) {
                            candidateDto = this.modelMapper.map(candidateStatus.getCandidate(), CandidateDetailsForReport.class);
                            candidateEmailStatus = candidateEmailStatusRepository.findByCandidateCandidateCode(candidateStatus.getCandidate().getCandidateCode());
                            candidateDto.setDateOfEmailInvite(candidateEmailStatus != null && candidateEmailStatus.getDateOfEmailInvite() != null ? candidateEmailStatus.getDateOfEmailInvite().toString() : null);
                            candidateDto.setStatusName(candidateStatus.getStatusMaster().getStatusName());
                            candidateDto.setStatusDate(candidateStatus.getLastUpdatedOn() != null ? candidateStatus.getLastUpdatedOn().toString() : null);
                            candidateDetailsDtoList.add(candidateDto);
                        }
                    }
                }
                if (reportSearchDto.getStatusCode().equals("REINVITE") || reportSearchDto.getStatusCode().equals("FINALREPORT") || reportSearchDto.getStatusCode().equals("PENDINGAPPROVAL")
                        || reportSearchDto.getStatusCode().equals("PROCESSDECLINED") || reportSearchDto.getStatusCode().equals("INVITATIONEXPIRED") || reportSearchDto.getStatusCode().equals("PENDINGNOW")) {
                    query = new StringBuilder();
                    query.append("select distinct cb.candidate_id ,um.user_first_name ,COALESCE(um.user_last_name,'') as lastname , ");
                    query.append("cb.candidate_name ,cb.contact_number ,cb.email_id,coalesce(cb.pan_number,'') as pannumber, ");
                    query.append("cb.applicant_id ,cb.candidate_code ,es.date_of_email_invite,cb.created_on, ");
                    query.append("coalesce(cb.experience_in_month,0) as noe, t.currentstatusdate, ");
                    query.append("tdcs.color_id ,sm.status_name ,t.invitationexpiredcount,t.reinvitecount,tdcs.last_updated_on as statusdate ");
                    query.append("from  t_dgv_candidate_basic cb, ");
                    query.append("t_dgv_candidate_status tdcs,t_dgv_status_master sm,t_dgv_user_master um, ");
                    query.append("t_dgv_candidate_email_status es , ");
                    query.append("( ");
                    query.append("SELECT csh.candidate_id,count(case when sm.status_code ='INVITATIONEXPIRED' then 1  END) as invitationexpiredcount, ");
                    query.append("count(case when sm.status_code ='REINVITE' then 1 END) as reinvitecount, ");
                    query.append("max(case when sm.status_code in (?4) then created_on END) as currentstatusdate ");
                    query.append("FROM t_dgv_candidate_status_history csh ");
                    query.append("left JOIN t_dgv_status_master sm ON sm.status_master_id=csh.status_master_id ");
                    query.append("where  csh.created_on  between ?1 and ?2 ");
                    query.append("and sm.status_code in (?4) ");
                    query.append("group by csh.candidate_id ) t ");
                    query.append("where ");
                    query.append(" cb.candidate_id=t.candidate_id ");
                    query.append("and sm.status_master_id=tdcs.status_master_id ");
                    query.append("and um.user_id =cb.created_by  ");
                    query.append("and es.candidate_id =cb.candidate_id  ");
                    query.append("and tdcs.candidate_id=cb.candidate_id ");
                    query.append("and cb.organization_id in (?3)  ");
                    if (reportSearchDto.getAgentIds() != null && !reportSearchDto.getAgentIds().isEmpty()) {
                        query.append("and um.user_id in (?5)  ");
                    }
                    squery = entityManager.createNativeQuery(String.valueOf(query));
                    squery.setParameter(1, startDate);
                    squery.setParameter(2, endDate);
                    squery.setParameter(3, reportSearchDto.getOrganizationIds());
                    if (reportSearchDto.getStatusCode().equals("PENDINGNOW")) {
                        statusList = new ArrayList<>();
                        Collections.addAll(statusList, "INVITATIONSENT", "ITR", "EPFO", "DIGILOCKER", "RELATIVEADDRESS");
                    } else {
                        statusList = new ArrayList<>();
                        Collections.addAll(statusList, reportSearchDto.getStatusCode());
                    }
                    squery.setParameter(4, statusList);
                    if (reportSearchDto.getAgentIds() != null && !reportSearchDto.getAgentIds().isEmpty()) {
                        squery.setParameter(5, reportSearchDto.getAgentIds());
                    }
                    resultList = squery.getResultList();
                    if (resultList != null && resultList.size() > 0) {
                        for (Object[] result : resultList) {
                            candidateDto = new CandidateDetailsForReport();
                            candidateDto.setCreatedByUserFirstName(String.valueOf(result[1]));
                            candidateDto.setCreatedByUserLastName(String.valueOf(result[2]));
                            candidateDto.setCandidateName(String.valueOf(result[3]));
                            candidateDto.setContactNumber(String.valueOf(result[4]));
                            candidateDto.setEmailId(String.valueOf(result[5]));
                            candidateDto.setPanNumber(String.valueOf(result[6]));
                            candidateDto.setApplicantId(String.valueOf(result[7]));
                            candidateDto.setCandidateCode(String.valueOf(result[8]));
                            candidateDto.setDateOfEmailInvite(String.valueOf(result[9]));
                            candidateDto.setCreatedOn(String.valueOf(result[10]));
                            candidateDto.setExperienceInMonth(Integer.valueOf(String.valueOf(result[11])));
                            candidateDto.setCurrentStatusDate(String.valueOf(result[12]));
                            candidateDto.setColorName(result[13] != null ? colorRepository.findById(Long.valueOf(String.valueOf(result[13]))).get().getColorName() : "NA");
                            candidateDto.setStatusName(String.valueOf(result[14]));
                            candidateDto.setNumberofexpiredCount(Integer.valueOf(String.valueOf(result[15])));
                            candidateDto.setReinviteCount(Integer.valueOf(String.valueOf(result[16])));
                            candidateDto.setStatusDate(String.valueOf(result[17]));
                            candidateDetailsDtoList.add(candidateDto);
                        }
                    }
                }
            }
            ReportSearchDto reportSearchDtoObj = new ReportSearchDto();
            reportSearchDtoObj.setFromDate(reportSearchDto.getFromDate());
            reportSearchDtoObj.setToDate(reportSearchDto.getToDate());
            reportSearchDtoObj.setStatusCode(reportSearchDto.getStatusCode());
            reportSearchDtoObj.setOrganizationIds(reportSearchDto.getOrganizationIds());
            if (reportSearchDto.getOrganizationIds() != null && reportSearchDto.getOrganizationIds().get(0) != 0) {
                reportSearchDtoObj.setOrganizationName(organizationRepository.findById(reportSearchDto.getOrganizationIds().get(0)).get().getOrganizationName());
            }
            List<CandidateDetailsForReport> sortedList = candidateDetailsDtoList.stream()
                    .sorted((o1, o2) -> o1.getCandidateName().compareTo(o2.getCandidateName()))
                    .collect(Collectors.toList());
            reportSearchDtoObj.setCandidateDetailsDto(sortedList);
            svcSearchResult.setData(reportSearchDtoObj);
            svcSearchResult.setOutcome(true);
            svcSearchResult.setMessage("SUCCESS");
        } catch (Exception ex) {
            log.error("Exception occured in getCanididateDetailsByStatus method in ReportServiceImpl-->", ex);
            svcSearchResult.setData(null);
            svcSearchResult.setOutcome(false);
            svcSearchResult.setMessage("Something Went Wrong, Please Try After Sometimes.");
        }
        return svcSearchResult;
    }

    @Override
    public ServiceOutcome<ReportSearchDto> eKycReportData(ReportSearchDto reportSearchDto) {
        ServiceOutcome<ReportSearchDto> svcSearchResult = new ServiceOutcome<ReportSearchDto>();
        List<CandidateDetailsForReport> candidateDetailsForReportList = new ArrayList<CandidateDetailsForReport>();
        CandidateDetailsForReport candidateDetailsForReport = null;
        ReportSearchDto reportSearchDtoObj = new ReportSearchDto();
        List<CandidateStatus> candidateList = null;
        List<Long> agentIds = new ArrayList<Long>();
        List<Long> orgIds = new ArrayList<Long>();
        List<String> statusList = new ArrayList<>();
        try {
            if (reportSearchDto != null && reportSearchDto.getUserId() != null) {
                User user = userRepository.findById(reportSearchDto.getUserId()).get();
                Collections.addAll(statusList, "PENDINGAPPROVAL", "FINALREPORT");
                if (reportSearchDto.getAgentIds() != null && !reportSearchDto.getAgentIds().isEmpty() && reportSearchDto.getAgentIds().get(0) != 0) {
                    candidateList = candidateStatusRepository.findAllByCreatedByUserIdInAndStatusMasterStatusCodeIn(reportSearchDto.getAgentIds(), statusList);
                } else {
                    if (user.getRole().getRoleCode().equals("ROLE_CBADMIN") || user.getRole().getRoleCode().equals("ROLE_ADMIN") || user.getRole().getRoleCode().equals("ROLE_PARTNERADMIN")) {
                        if (user.getRole().getRoleCode().equals("ROLE_CBADMIN")) {
                            if (reportSearchDto.getOrganizationIds() != null && !reportSearchDto.getOrganizationIds().isEmpty() && reportSearchDto.getOrganizationIds().get(0) != 0) {
                                orgIds = reportSearchDto.getOrganizationIds();
                            } else {
                                ServiceOutcome<List<OrganizationDto>> svcoutcome = organizationServiceImpl.getOrganizationListAfterBilling();
                                orgIds = svcoutcome.getData().parallelStream().map(x -> x.getOrganizationId()).collect(Collectors.toList());
                            }
                        } else {
                            orgIds.add(0, user.getOrganization().getOrganizationId());
                        }
                        candidateList = candidateStatusRepository.findAllByCandidateOrganizationOrganizationIdInAndStatusMasterStatusCodeIn(orgIds, statusList);
                    }
                    if (user.getRole().getRoleCode().equals("ROLE_AGENTSUPERVISOR") || user.getRole().getRoleCode().equals("ROLE_AGENTHR")) {
                        List<User> agentList = userRepository.findAllByAgentSupervisorUserId(user.getUserId());
                        if (!agentList.isEmpty()) {
                            agentIds = agentList.parallelStream().map(x -> x.getUserId()).collect(Collectors.toList());
                        }
                        agentIds.add(user.getUserId());
                        candidateList = candidateStatusRepository.findAllByCreatedByUserIdInAndStatusMasterStatusCodeIn(agentIds, statusList);
                    }
                }
                if (candidateList != null && !candidateList.isEmpty()) {
                    for (CandidateStatus candidate : candidateList) {
                        candidateDetailsForReport = this.modelMapper.map(candidate.getCandidate(), CandidateDetailsForReport.class);
                        List<String> uanNUmberList = candidateCafExperienceRepository.getCandidateUan(candidate.getCandidate().getCandidateId());
                        String uanNumber = uanNUmberList.parallelStream().map(uan -> uan.toString()).collect(Collectors.joining("/"));
                        candidateDetailsForReport.setCandidateUan(uanNumber);


                        CandidateIdItems candidateIdItemPan = candidateIdItemsRepository.findByCandidateCandidateCodeAndServiceSourceMasterServiceCode(candidate.getCandidate().getCandidateCode(), "PAN");
                        if (candidateIdItemPan != null && candidateIdItemPan.getIdHolder() != null) {
                            candidateDetailsForReport.setPanName(candidateIdItemPan.getIdHolder());
                            candidateDetailsForReport.setPanDob(candidateIdItemPan.getIdHolderDob());
                        }
                        List<EpfoData> uanList = epfoDataRepository.findAllByCandidateCandidateId(candidate.getCandidate().getCandidateId());
                        String uanName = uanList.parallelStream().map(name -> name.toString()).collect(Collectors.joining("/"));
                        for (EpfoData uan : uanList) {
                            candidateDetailsForReport.setCandidateUanName(uan.getName());
                        }
                        CandidateCafAddress candidateCafAddress = candidateCafAddressRepository.findByCandidateCandidateCodeAndServiceSourceMasterServiceCodeAndAddressVerificationIsNull(candidate.getCandidate().getCandidateCode(), "AADHARADDR");
                        candidateDetailsForReport.setAddress(candidateCafAddress != null ? candidateCafAddress.getCandidateAddress() : "");

                        CandidateCafAddress candidateCafAddressRelation = candidateCafAddressRepository.findByCandidateCandidateCodeAndAddressVerificationIsNotNull(candidate.getCandidate().getCandidateCode());
                        candidateDetailsForReport.setRelationship(candidateCafAddressRelation != null && candidateCafAddressRelation.getAddressVerification() != null ? candidateCafAddressRelation.getAddressVerification().getCandidateCafRelationship().getCandidateRelationship() : "");
                        candidateDetailsForReport.setRelationName(candidateCafAddressRelation != null && candidateCafAddressRelation.getAddressVerification() != null ? candidateCafAddressRelation.getName() : "");
                        candidateDetailsForReportList.add(candidateDetailsForReport);
                    }
                }
                List<CandidateDetailsForReport> sortedList = candidateDetailsForReportList.parallelStream()
                        .sorted((o1, o2) -> o1.getCandidateName().compareTo(o2.getCandidateName()))
                        .collect(Collectors.toList());
                reportSearchDtoObj.setCandidateDetailsDto(sortedList);
                reportSearchDtoObj.setUserId(reportSearchDto.getUserId());
                reportSearchDtoObj.setOrganizationIds(reportSearchDto.getOrganizationIds());
                reportSearchDtoObj.setAgentIds(reportSearchDto.getAgentIds());
                svcSearchResult.setData(reportSearchDtoObj);
                svcSearchResult.setOutcome(true);
                svcSearchResult.setMessage("SUCCESS");
            } else {
                svcSearchResult.setData(null);
                svcSearchResult.setOutcome(false);
                svcSearchResult.setMessage("Please specify user.");
            }
        } catch (Exception ex) {
            log.error("Exception occured in eKycReportData method in ReportServiceImpl-->", ex);
            svcSearchResult.setData(null);
            svcSearchResult.setOutcome(false);
            svcSearchResult.setMessage("Something Went Wrong, Please Try After Sometimes.");
        }
        return svcSearchResult;
    }


    @Override
    public String generateDocument(String candidateCode, String token, ReportType reportType) {
        System.out.println("enter to generate doc *******************************");
        Candidate candidate = candidateService.findCandidateByCandidateCode(
                candidateCode);
        CandidateAddComments candidateAddComments = candidateAddCommentRepository.findByCandidateCandidateId(candidate.getCandidateId());
        System.out.println(candidate.getCandidateId() + "*******************************" + validateCandidateStatus(candidate.getCandidateId()));
//        if (validateCandidateStatus(candidate.getCandidateId())) {
        if (candidate != null) {
            System.out.println("enter if *******************************");
            List<VendorUploadChecksDto> vendordocDtoList = new ArrayList<VendorUploadChecksDto>();
            VendorUploadChecksDto vendorUploadChecksDto = null;

            // candidate Basic detail
            CandidateReportDTO candidateReportDTO = new CandidateReportDTO();
            candidateReportDTO.setName(candidate.getCandidateName());
            candidateReportDTO.setApplicantId(candidate.getApplicantId());
            candidateReportDTO.setDob(candidate.getDateOfBirth());
            candidateReportDTO.setContactNo(candidate.getContactNumber());
            candidateReportDTO.setEmailId(candidate.getEmailId());
//            candidateReportDTO.setExperience(candidate.getIsFresher() ? "Fresher" : "Experience");
            candidateReportDTO.setReportType(reportType);
            Organization organization = candidate.getOrganization();
            candidateReportDTO.setOrganizationName(organization.getOrganizationName());
            candidateReportDTO.setProject(organization.getOrganizationName());
            candidateReportDTO.setOrganizationLocation(organization.getOrganizationLocation());
            candidateReportDTO.setOrganizationLogo(organization.getLogoUrl());
            if (candidateAddComments != null) {
                candidateReportDTO.setComments(candidateAddComments.getComments());
            }


            CandidateVerificationState candidateVerificationState = candidateService.getCandidateVerificationStateByCandidateId(candidate.getCandidateId());
            boolean hasCandidateVerificationStateChanged = false;
            if (Objects.isNull(candidateVerificationState)) {
                candidateVerificationState = new CandidateVerificationState();
                candidateVerificationState.setCandidate(candidate);
                final ZoneId id = ZoneId.systemDefault();
                candidateVerificationState.setCaseInitiationTime(ZonedDateTime.ofInstant(candidate.getCreatedOn().toInstant(), id));

            }
            switch (reportType) {
                case PRE_OFFER:
                    candidateVerificationState.setPreApprovalTime(ZonedDateTime.now());
                    break;
                case FINAL:
                    candidateVerificationState.setFinalReportTime(ZonedDateTime.now());
                    break;
                case INTERIM:
                    candidateVerificationState.setInterimReportTime(ZonedDateTime.now());
                    break;

            }
            candidateVerificationState = candidateService.addOrUpdateCandidateVerificationStateByCandidateId(
                    candidate.getCandidateId(), candidateVerificationState);
            candidateReportDTO.setFinalReportDate(DateUtil.convertToString(ZonedDateTime.now()));
            candidateReportDTO.setInterimReportDate(DateUtil.convertToString(candidateVerificationState.getInterimReportTime()));
            candidateReportDTO.setCaseInitiationDate(DateUtil.convertToString(candidateVerificationState.getCaseInitiationTime()));
            // executive summary
            Long organizationId = organization.getOrganizationId();
            List<OrganizationExecutive> organizationExecutiveByOrganizationId = organizationService.getOrganizationExecutiveByOrganizationId(
                    organizationId);
            List<ExecutiveSummaryDto> executiveSummaryDtos = new ArrayList<>();
            organizationExecutiveByOrganizationId.stream().forEach(organizationExecutive -> {
                switch (organizationExecutive.getExecutive().getName()) {

                    // System.out.println(organizationExecutive.getExecutive());
                    case EDUCATION:
                        System.out.println("inside EDUCATION *******************************");
                        List<CandidateCafEducationDto> candidateCafEducationDtos = candidateService.getAllCandidateEducationByCandidateId(
                                candidate.getCandidateId());
                        List<EducationVerificationDTO> educationVerificationDTOS = candidateCafEducationDtos.stream()
                                .map(candidateCafEducationDto -> {
                                    EducationVerificationDTO educationVerificationDTO = new EducationVerificationDTO();
                                    educationVerificationDTO.setVerificationStatus(VerificationStatus.valueOf(candidateCafEducationDto.getColorColorCode()));
                                    educationVerificationDTO.setSource(SourceEnum.DIGILOCKER);
                                    educationVerificationDTO.setDegree(candidateCafEducationDto.getCourseName());
                                    educationVerificationDTO.setUniversity(
                                            candidateCafEducationDto.getBoardOrUniversityName());
                                    return educationVerificationDTO;
                                }).collect(Collectors.toList());
                        candidateReportDTO.setEducationVerificationDTOList(educationVerificationDTOS);
                        List<String> redArray = new ArrayList<>();
                        ;
                        List<String> amberArray = new ArrayList<>();
                        ;
                        List<String> greenArray = new ArrayList<>();
                        ;
                        String status = null;
                        for (EducationVerificationDTO s : educationVerificationDTOS) {
                            if (s.getVerificationStatus().equals(VerificationStatus.RED)) {
                                redArray.add("count");
                            } else if (s.getVerificationStatus().equals(VerificationStatus.AMBER)) {
                                amberArray.add("count");
                            } else {
                                greenArray.add("count");
                            }
                        }
                        if (redArray.size() > 0) {
                            status = VerificationStatus.RED.toString();
                        } else if (amberArray.size() > 0) {
                            status = VerificationStatus.AMBER.toString();
                        } else {
                            status = VerificationStatus.GREEN.toString();
                        }
                        candidateReportDTO.setEducationConsolidatedStatus(status);
                        break;
                    case IDENTITY:
                        System.out.println("inside identity *******************************");
                        // verify from digilocker and itr
                        List<IDVerificationDTO> idVerificationDTOList = new ArrayList<>();
                        IDVerificationDTO aadhaarIdVerificationDTO = new IDVerificationDTO();
                        aadhaarIdVerificationDTO.setName(candidate.getAadharName());
                        aadhaarIdVerificationDTO.setIDtype(IDtype.AADHAAR.label);
                        aadhaarIdVerificationDTO.setIdNo(candidate.getAadharNumber());
                        aadhaarIdVerificationDTO.setSourceEnum(SourceEnum.DIGILOCKER);
                        aadhaarIdVerificationDTO.setVerificationStatus(VerificationStatus.GREEN);
                        idVerificationDTOList.add(aadhaarIdVerificationDTO);

                        IDVerificationDTO panIdVerificationDTO = new IDVerificationDTO();
                        panIdVerificationDTO.setName(candidate.getCandidateName());
                        panIdVerificationDTO.setIDtype(IDtype.PAN.label);
                        panIdVerificationDTO.setIdNo(candidate.getPanNumber());
                        panIdVerificationDTO.setSourceEnum(SourceEnum.DIGILOCKER);
                        panIdVerificationDTO.setVerificationStatus(VerificationStatus.GREEN);
                        idVerificationDTOList.add(panIdVerificationDTO);

                        List<CandidateEPFOResponse> uanList = candidateEPFOResponseRepository.findByCandidateId(
                                candidate.getCandidateId());
                        for (CandidateEPFOResponse candidateEPFOResponse : uanList) {
                            IDVerificationDTO uanIdVerificationDTO = new IDVerificationDTO();
                            uanIdVerificationDTO.setName(candidate.getCandidateName());
                            uanIdVerificationDTO.setIDtype(IDtype.UAN.label);
                            uanIdVerificationDTO.setIdNo(candidateEPFOResponse.getUan());
                            uanIdVerificationDTO.setSourceEnum(SourceEnum.EPFO);
                            uanIdVerificationDTO.setVerificationStatus(VerificationStatus.GREEN);
                            idVerificationDTOList.add(uanIdVerificationDTO);
                        }
                        List<String> redArray_id = new ArrayList<>();
                        ;
                        List<String> amberArray_id = new ArrayList<>();
                        ;
                        List<String> greenArray_id = new ArrayList<>();
                        ;
                        String status_id = null;
                        for (IDVerificationDTO s : idVerificationDTOList) {
                            if (s.getVerificationStatus().equals(VerificationStatus.RED)) {
                                redArray_id.add("count");
                            } else if (s.getVerificationStatus().equals(VerificationStatus.AMBER)) {
                                amberArray_id.add("count");
                            } else {
                                greenArray_id.add("count");
                            }
                        }
                        if (redArray_id.size() > 0) {
                            status_id = VerificationStatus.RED.toString();
                        } else if (amberArray_id.size() > 0) {
                            status_id = VerificationStatus.AMBER.toString();
                        } else {
                            status_id = VerificationStatus.GREEN.toString();
                        }

                        System.out.println("befor epfo *******************************");
                        candidateReportDTO.setIdVerificationDTOList(idVerificationDTOList);
                        candidateReportDTO.setIdConsolidatedStatus(status_id);
                        PanCardVerificationDto panCardVerificationDto = new PanCardVerificationDto();
                        panCardVerificationDto.setInput(candidate.getPanNumber());
                        panCardVerificationDto.setOutput(candidate.getPanNumber());
                        panCardVerificationDto.setSource(SourceEnum.DIGILOCKER);
                        panCardVerificationDto.setVerificationStatus(VerificationStatus.GREEN);
                        candidateReportDTO.setPanCardVerification(panCardVerificationDto);
                        executiveSummaryDtos.add(new ExecutiveSummaryDto(ExecutiveName.IDENTITY, "Pan", VerificationStatus.GREEN));
//
                        AadharVerificationDTO aadharVerification = new AadharVerificationDTO();
                        aadharVerification.setAadharNo(candidate.getAadharNumber());
                        aadharVerification.setName(candidate.getAadharName());
                        aadharVerification.setFatherName(candidate.getAadharFatherName());
                        aadharVerification.setDob(candidate.getAadharDob());
                        aadharVerification.setSource(SourceEnum.DIGILOCKER);
                        candidateReportDTO.setAadharCardVerification(aadharVerification);
                        executiveSummaryDtos.add(new ExecutiveSummaryDto(ExecutiveName.IDENTITY, "Aadhar", VerificationStatus.GREEN));
                        break;
                    case EMPLOYMENT:
                        System.out.println("empy *******************************");
                        List<CandidateCafExperience> candidateCafExperienceList = candidateService.getCandidateExperienceByCandidateId(candidate.getCandidateId());

                        Collections.sort(candidateCafExperienceList, new Comparator<CandidateCafExperience>() {
                            @Override
                            public int compare(CandidateCafExperience o1, CandidateCafExperience o2) {
                                return o1.getInputDateOfJoining().compareTo(o2.getInputDateOfJoining());
                            }
                        });
                        Collections.reverse(candidateCafExperienceList);
                        cleanDate(candidateCafExperienceList);
                        List<CandidateCafExperience> candidateExperienceFromItrEpfo = candidateService.getCandidateExperienceFromItrAndEpfoByCandidateId(
                                candidate.getCandidateId(), true);
                        cleanDate(candidateExperienceFromItrEpfo);
                        ServiceOutcome<ToleranceConfig> toleranceConfigByOrgId = organizationService.getToleranceConfigByOrgId(
                                organizationId);
                        // System.out.println(candidateCafExperienceList+"candidateCafExperienceList");
                        if (!candidateCafExperienceList.isEmpty()) {
                            System.out.println("inside exp if");
                            // validate experience and tenure
                            List<EmploymentVerificationDto> employmentVerificationDtoList = validateAndCompareExperience(candidateCafExperienceList, candidateExperienceFromItrEpfo, toleranceConfigByOrgId.getData());
                            employmentVerificationDtoList.sort(Comparator.comparing(EmploymentVerificationDto::getDoj).reversed());
                            candidateReportDTO.setEmploymentVerificationDtoList(employmentVerificationDtoList);

                            List<EmploymentTenureVerificationDto> employmentTenureDtoList = validateAndCompareExperienceTenure(employmentVerificationDtoList, candidateExperienceFromItrEpfo, toleranceConfigByOrgId.getData());
                            employmentTenureDtoList.sort(Comparator.comparing(EmploymentTenureVerificationDto::getDoj).reversed());
                            candidateReportDTO.setEmploymentTenureVerificationDtoList(employmentTenureDtoList);
                            List<String> redArray_emp = new ArrayList<>();
                            ;
                            List<String> amberArray_emp = new ArrayList<>();
                            ;
                            List<String> greenArray_emp = new ArrayList<>();
                            ;
                            String status_emp = null;
                            for (EmploymentTenureVerificationDto s : employmentTenureDtoList) {
                                if (s.getVerificationStatus().equals(VerificationStatus.RED)) {
                                    redArray_emp.add("count");
                                } else if (s.getVerificationStatus().equals(VerificationStatus.AMBER)) {
                                    amberArray_emp.add("count");
                                } else {
                                    greenArray_emp.add("count");
                                }
                            }
                            if (redArray_emp.size() > 0) {
                                status_emp = VerificationStatus.RED.toString();
                            } else if (amberArray_emp.size() > 0) {
                                status_emp = VerificationStatus.AMBER.toString();
                            } else {
                                status_emp = VerificationStatus.GREEN.toString();
                            }
                            candidateReportDTO.setEmploymentConsolidatedStatus(status_emp);
                            candidateCafExperienceList.sort(Comparator.comparing(CandidateCafExperience::getInputDateOfJoining).reversed());
                            candidateReportDTO.setInputExperienceList(candidateCafExperienceList);
                            EPFODataDto epfoDataDto = new EPFODataDto();

                            Optional<CandidateEPFOResponse> canditateItrEpfoResponseOptional = candidateEPFOResponseRepository.findByCandidateId(
                                    candidate.getCandidateId()).stream().findFirst();
                            if (canditateItrEpfoResponseOptional.isPresent()) {
                                String epfoResponse = canditateItrEpfoResponseOptional.get().getEPFOResponse();
                                try {
                                    ObjectMapper objectMapper = new ObjectMapper();
                                    JsonNode arrNode = objectMapper.readTree(epfoResponse).get("message");
                                    List<EpfoDataResDTO> epfoDatas = new ArrayList<>();
                                    if (arrNode.isArray()) {
                                        for (final JsonNode objNode : arrNode) {
                                            EpfoDataResDTO epfoData = new EpfoDataResDTO();
                                            epfoData.setName(objNode.get("name").asText());
                                            epfoData.setUan(objNode.get("uan").asText());
                                            epfoData.setCompany(objNode.get("company").asText());
                                            epfoData.setDoe(objNode.get("doe").asText());
                                            epfoData.setDoj(objNode.get("doj").asText());
                                            epfoDatas.add(epfoData);
                                        }
                                    }

                                    epfoDataDto.setCandidateName(epfoDatas.stream().map(EpfoDataResDTO::getName).filter(StringUtils::isNotEmpty).findFirst().orElse(null));
                                    epfoDataDto.setUANno(canditateItrEpfoResponseOptional.get().getUan());
                                    epfoDataDto.setEpfoDataList(epfoDatas);
                                    candidateReportDTO.setEpfoData(epfoDataDto);
                                } catch (JsonProcessingException e) {
                                    e.printStackTrace();
                                }
                            }


                            List<ITRData> itrDataList = itrDataRepository.findAllByCandidateCandidateCodeOrderByFiledDateDesc(
                                    candidateCode);
                            ITRDataDto itrDataDto = new ITRDataDto();
                            itrDataDto.setItrDataList(itrDataList);
                            candidateReportDTO.setItrData(itrDataDto);

                            // System.out.println(candidateReportDTO.getEmploymentVerificationDtoList()+"candidateReportDTO");
                            for (EmploymentVerificationDto employmentVerificationDto : candidateReportDTO.getEmploymentVerificationDtoList()) {
                                // System.out.println("inside for"+employmentVerificationDto+"emppppp"+candidateReportDTO);
                                executiveSummaryDtos.add(new ExecutiveSummaryDto(ExecutiveName.EMPLOYMENT, employmentVerificationDto.getInput(), employmentVerificationDto.getVerificationStatus()));
                            }


                        }

                        break;
                    case ADDRESS:

                        List<CandidateCafAddressDto> candidateAddress = candidateService.getCandidateAddress(candidate);
                        System.out.println("ADDRESS**************" + candidateAddress);
                        List<AddressVerificationDto> collect = candidateAddress.stream().map(candidateCafAddressDto -> {
                            AddressVerificationDto addressVerificationDto = new AddressVerificationDto();
                            addressVerificationDto.setInput(candidateCafAddressDto.getCandidateAddress());
                            addressVerificationDto.setVerificationStatus(VerificationStatus.GREEN);
                            addressVerificationDto.setSource(SourceEnum.DIGILOCKER);
                            List<String> type = new ArrayList<>();
                            // 	if(candidateCafAddressDto.getIsAssetDeliveryAddress()) {
                            // 		type.add("Communication");
                            // 	}  if(candidateCafAddressDto.getIsPresentAddress()) {
                            // 		type.add("Present");

                            // 	}  if(candidateCafAddressDto.getIsPermanentAddress()) {
                            // 		type.add("Premanent");
                            // 	}
                            // 	addressVerificationDto.setType(String.join(", ", type));
                            return addressVerificationDto;
                        }).collect(Collectors.toList());
                        List<String> redArray_addr = new ArrayList<>();
                        ;
                        List<String> amberArray_addr = new ArrayList<>();
                        ;
                        List<String> greenArray_addr = new ArrayList<>();
                        ;
                        String status_addr = null;
                        for (AddressVerificationDto s : collect) {
                            if (s.getVerificationStatus().equals(VerificationStatus.RED)) {
                                redArray_addr.add("count");
                            } else if (s.getVerificationStatus().equals(VerificationStatus.AMBER)) {
                                amberArray_addr.add("count");
                            } else {
                                greenArray_addr.add("count");
                            }
                        }
                        if (redArray_addr.size() > 0) {
                            status_addr = VerificationStatus.RED.toString();
                        } else if (amberArray_addr.size() > 0) {
                            status_addr = VerificationStatus.AMBER.toString();
                        } else {
                            status_addr = VerificationStatus.GREEN.toString();
                        }
                        candidateReportDTO.setAddressConsolidatedStatus(status_addr);
                        candidateReportDTO.setAddressVerificationDtoList(collect);
                        System.out.println("candidateReportDTO**************" + candidateReportDTO);
                        break;
                    case CRIMINAL:
                        break;
                    case REFERENCE_CHECK_1:
                        break;
                    case REFERENCE_CHECK_2:
                        break;
                }

                System.out.println("switch  *******************************");
                candidateReportDTO.setExecutiveSummaryList(executiveSummaryDtos);
                System.out.println("switch end *******************************");

            });
            // System.out.println("before pdf*******************************"+candidateReportDTO);

            List<VendorChecks> vendorList = vendorChecksRepository.findAllByCandidateCandidateId(candidate.getCandidateId());
            for (VendorChecks vendorChecks : vendorList) {
                User user = userRepository.findByUserId(vendorChecks.getVendorId());
                VendorUploadChecks vendorChecksss = vendorUploadChecksRepository.findByVendorChecksVendorcheckId(vendorChecks.getVendorcheckId());
                if (vendorChecksss != null) {
                    vendorUploadChecksDto = new VendorUploadChecksDto(user.getUserFirstName(), vendorChecksss.getVendorChecks().getVendorcheckId(), vendorChecksss.getVendorUploadedDocument(), vendorChecksss.getDocumentname(), vendorChecksss.getAgentColor().getColorName(), vendorChecksss.getAgentColor().getColorHexCode(), null);
                    vendordocDtoList.add(vendorUploadChecksDto);
                }
            }

            candidateReportDTO.setVendorProofDetails(vendordocDtoList);

            updateCandidateVerificationStatus(candidateReportDTO);
            System.out.println("after*****************update**************");
            Date createdOn = candidate.getCreatedOn();
            System.out.println("after *****************date**************");

            System.out.println("candidate Report dto : " + candidateReportDTO);
            File report = FileUtil.createUniqueTempFile("report", ".pdf");
            String htmlStr = pdfService.parseThymeleafTemplate("pdf", candidateReportDTO);
            // String htmlStr = "";
            // if(reportType.equals(ReportType.FINAL)) {
            // 	 htmlStr = pdfService.parseThymeleafTemplate("final", candidateReportDTO);
            // }else {
            //      htmlStr = pdfService.parseThymeleafTemplate("pdf", candidateReportDTO);
            // }
            pdfService.generatePdfFromHtml(htmlStr, report);
            List<Content> contentList = contentRepository.findAllByCandidateIdAndContentTypeIn(
                    candidate.getCandidateId(), Arrays.asList(ContentType.ISSUED, ContentType.AGENT_UPLOADED));

            List<File> files = contentList.stream().map(content -> {
                System.out.println("**************************");
                File uniqueTempFile = FileUtil.createUniqueTempFile(candidateCode + "_issued_" + content.getContentId().toString(), ".pdf");
                awsUtils.getFileFromS3(content.getBucketName(), content.getPath(), uniqueTempFile);
                return uniqueTempFile;
            }).collect(Collectors.toList());

            List<String> vendorFilesURLs_paths = vendordocDtoList.stream().map(vendor -> {
                byte[] data = vendor.getDocument();
                String vendorFilesTemp = "Candidate/".concat(createdOn.toString()).concat(candidateCode + "/Generated" + vendor.getDocumentname());
                String s = awsUtils.uploadFileAndGetPresignedUrl_bytes(DIGIVERIFIER_DOC_BUCKET_NAME, vendorFilesTemp, data);
                System.out.println("precised" + s);
                return vendorFilesTemp;
            }).collect(Collectors.toList());

            List<File> vendorfiles = vendorFilesURLs_paths.stream().map(content -> {
                System.out.println("**************************");
                File uniqueTempFile = FileUtil.createUniqueTempFile(content, ".pdf");
                awsUtils.getFileFromS3(DIGIVERIFIER_DOC_BUCKET_NAME, content, uniqueTempFile);
                return uniqueTempFile;
            }).collect(Collectors.toList());


            try {
                System.out.println("entry to generate try*************************");
                File mergedFile = FileUtil.createUniqueTempFile(candidateCode, ".pdf");
                List<InputStream> collect = new ArrayList<>();
                collect.add(FileUtil.convertToInputStream(report));
                collect.addAll(files.stream().map(FileUtil::convertToInputStream).collect(Collectors.toList()));


                collect.addAll(vendorfiles.stream().map(FileUtil::convertToInputStream).collect(Collectors.toList()));
                PdfUtil.mergePdfFiles(collect, new FileOutputStream(mergedFile.getPath()));

                String path = "Candidate/".concat(
                        candidateCode + "/Generated".concat("/").concat(reportType.name()).concat(".pdf"));
                String pdfUrl = awsUtils.uploadFileAndGetPresignedUrl(DIGIVERIFIER_DOC_BUCKET_NAME, path, mergedFile);
                Content content = new Content();
                content.setCandidateId(candidate.getCandidateId());
                content.setContentCategory(ContentCategory.OTHERS);
                content.setContentSubCategory(ContentSubCategory.PRE_APPROVAL);
                // System.out.println(content+"*******************************************content");
                if (reportType.name().equalsIgnoreCase("PRE_OFFER")) {
                    content.setContentSubCategory(ContentSubCategory.PRE_APPROVAL);
                } else if (reportType.name().equalsIgnoreCase("FINAL")) {
                    content.setContentSubCategory(ContentSubCategory.FINAL);
                }
                content.setFileType(FileType.PDF);
                content.setContentType(ContentType.GENERATED);
                content.setBucketName(DIGIVERIFIER_DOC_BUCKET_NAME);
                content.setPath(path);
                contentRepository.save(content);
                String reportTypeStr = reportType.label;
                Email email = new Email();
                email.setSender(emailProperties.getDigiverifierEmailSenderId());
                User agent = candidate.getCreatedBy();
                email.setReceiver(agent.getUserEmailId());
                email.setTitle("DigiVerifier " + reportTypeStr + " report - " + candidate.getCandidateName());

                email.setAttachmentName(candidateCode + " " + reportTypeStr + ".pdf");
                email.setAttachmentFile(mergedFile);

                email.setContent(String.format(emailContent, agent.getUserFirstName(), candidate.getCandidateName(), reportTypeStr));
                emailSentTask.send(email);
                // delete files
                files.stream().forEach(file -> file.delete());
                mergedFile.delete();
                report.delete();
                return pdfUrl;
            } catch (FileNotFoundException | MessagingException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return null;

        } else {
            System.out.println("enter else");
            throw new RuntimeException("unable to generate document for this candidate");
        }
    }


    private void cleanDate(List<CandidateCafExperience> candidateCafExperienceList) {
        if (candidateCafExperienceList.size() >= 1 && Objects.isNull(
                candidateCafExperienceList.stream().findFirst().get().getInputDateOfExit())) {
            candidateCafExperienceList.stream().findFirst().get().setInputDateOfExit(new Date());
        }

        for (int i = 1; i < candidateCafExperienceList.size(); i++) {
            CandidateCafExperience candidateCafExperience = candidateCafExperienceList.get(i);
            if (Objects.isNull(candidateCafExperience.getInputDateOfExit())) {
                Date nextInputDateOfJoining = candidateCafExperienceList.get(i - 1).getInputDateOfJoining();
                LocalDateTime ldt = LocalDateTime.ofInstant(nextInputDateOfJoining.toInstant(), ZoneId.systemDefault());
                LocalDateTime exitDate = ldt.minusDays(1);
                Date out = Date.from(exitDate.atZone(ZoneId.systemDefault()).toInstant());
                candidateCafExperience.setInputDateOfExit(out);
            }
        }
    }

    private void updateCandidateVerificationStatus(CandidateReportDTO candidateReportDTO) {
        System.out.println("entry private------------" + candidateReportDTO.getExecutiveSummaryList());
        List<VerificationStatus> collect = candidateReportDTO.getExecutiveSummaryList().stream()
                .map(ExecutiveSummaryDto::getVerificationStatus).collect(Collectors.toList());
        System.out.println("private----inisde--------" + collect);
        if (collect.contains(VerificationStatus.RED)) {
            System.out.println("entry if------------");
            candidateReportDTO.setVerificationStatus(VerificationStatus.RED);
        } else if (collect.contains(VerificationStatus.AMBER)) {
            System.out.println("entry else------if------");
            candidateReportDTO.setVerificationStatus(VerificationStatus.AMBER);
        } else {
            System.out.println("entry------else------");
            candidateReportDTO.setVerificationStatus(VerificationStatus.GREEN);
        }
    }

    private List<EmploymentTenureVerificationDto> validateAndCompareExperienceTenure(
            List<EmploymentVerificationDto> candidateCafExperienceList,
            List<CandidateCafExperience> candidateExperienceFromItrEpfo,
            ToleranceConfig toleranceConfig) {
        return candidateCafExperienceList.stream().map(employmentVerificationDto -> {
            EmploymentTenureVerificationDto employmentTenureVerificationDto = new EmploymentTenureVerificationDto();
            long inputDifference1 = DateUtil.differenceInMonths(employmentVerificationDto.getDoj(),
                    employmentVerificationDto.getDoe());
            employmentTenureVerificationDto.setDoj(employmentVerificationDto.getDoj());
            employmentTenureVerificationDto.setInput(inputDifference1 / 12 + " Years, " + inputDifference1 % 12 + " months");
            employmentTenureVerificationDto.setOutput("Data Not Found");
            employmentTenureVerificationDto.setVerificationStatus(VerificationStatus.AMBER);
            Integer tenure = toleranceConfig.getTenure();
            employmentTenureVerificationDto.setEmployerName(employmentVerificationDto.getInput());
            if (employmentVerificationDto.getVerificationStatus().equals(VerificationStatus.GREEN)) {
                CandidateCafExperience candidateCafExperience = candidateExperienceFromItrEpfo.get(
                        employmentVerificationDto.getIndex());
                long inputDifference = DateUtil.differenceInMonths(employmentVerificationDto.getDoj(),
                        employmentVerificationDto.getDoe());
                employmentTenureVerificationDto.setInput(inputDifference / 12 + " Years, " + inputDifference % 12 + " months");

                long outputDifference = DateUtil.differenceInMonths(candidateCafExperience.getInputDateOfJoining(),
                        candidateCafExperience.getInputDateOfExit());
                employmentTenureVerificationDto.setOutput(outputDifference / 12 + " Years, " + outputDifference % 12 + " months");

                if (Math.abs(inputDifference - outputDifference) <= tenure) {
                    employmentTenureVerificationDto.setSource(employmentVerificationDto.getSource());
                    employmentTenureVerificationDto.setVerificationStatus(VerificationStatus.GREEN);
                }
            }
            return employmentTenureVerificationDto;
        }).collect(Collectors.toList());
//			Optional<CandidateCafExperience> experienceOptional = candidateExperienceFromItrEpfo.stream()
//				.filter(candidateCafExperienceItrEpfo -> {
//					int dojDifference = DateUtil.differenceInMonths(employmentVerificationDto.getInputDateOfJoining(),
//						candidateCafExperienceItrEpfo.getInputDateOfJoining());
//					int doeDifference = DateUtil.differenceInMonths(employmentVerificationDto.getInputDateOfExit(),
//						candidateCafExperienceItrEpfo.getInputDateOfExit());
//					return doeDifference <= tenure && dojDifference <= tenure;
//				}).findFirst();
//			if(experienceOptional.isPresent()){
//				String employerNameOp = employmentVerificationDto.getCandidateEmployerName();
//				employmentTenureVerificationDto.setOutput(employerNameOp);
//				employmentTenureVerificationDto.setVerificationStatus(VerificationStatus.GREEN);
//			}else{
//				employmentTenureVerificationDto.setVerificationStatus(VerificationStatus.RED);
//				employmentTenureVerificationDto.setOutput(NO_DATA_FOUND);
//			}
//
//			return employmentTenureVerificationDto;
//		}).collect(Collectors.toList());
    }

    private List<EmploymentVerificationDto> validateAndCompareExperience(
            List<CandidateCafExperience> candidateCafExperienceList,
            List<CandidateCafExperience> candidateExperienceFromItrEpfo,
            ToleranceConfig toleranceConfig) {
        List<EmploymentVerificationDto> employmentVerificationDtoList = candidateCafExperienceList.stream().map(candidateCafExperience -> {
            EmploymentVerificationDto employmentVerificationDto = new EmploymentVerificationDto();
            employmentVerificationDto.setInput(candidateCafExperience.getCandidateEmployerName());
            employmentVerificationDto.setVerificationStatus(VerificationStatus.AMBER);
            employmentVerificationDto.setOutput("Data Not Found");
            employmentVerificationDto.setDoj(candidateCafExperience.getInputDateOfJoining());
            employmentVerificationDto.setDoe(candidateCafExperience.getInputDateOfExit());
            return employmentVerificationDto;
        }).collect(Collectors.toList());
        for (EmploymentVerificationDto employmentVerificationDto : employmentVerificationDtoList) {
            int index = 0;
            List<String> outputNameSet = candidateExperienceFromItrEpfo.stream()
                    .map(CandidateCafExperience::getCandidateEmployerName).collect(
                            Collectors.toList());
            while (index < outputNameSet.size() && employmentVerificationDto.getVerificationStatus().equals(VerificationStatus.AMBER)) {
                String employerName1 = outputNameSet.get(index);
                double similarity = CommonUtils.checkStringSimilarity(employmentVerificationDto.getInput(), employerName1);
                if (similarity >= 0.75) {
                    employmentVerificationDto.setVerificationStatus(VerificationStatus.GREEN);
                    employmentVerificationDto.setOutput(employerName1);
                    employmentVerificationDto.setSource(SourceEnum.valueOf(candidateExperienceFromItrEpfo.get(index).getServiceSourceMaster().getServiceCode()));
                    outputNameSet.remove(index);
                    employmentVerificationDto.setIndex(index);
                    employmentVerificationDto.setDoj(candidateExperienceFromItrEpfo.get(index).getInputDateOfJoining());
                    Date inputDateOfExit = candidateExperienceFromItrEpfo.get(index).getInputDateOfExit();
                    if (Objects.isNull(inputDateOfExit) && index == 0) {
                        employmentVerificationDto.setDoe(new Date());
                    } else {
                        employmentVerificationDto.setDoe(inputDateOfExit);
                    }
                }
                index++;
            }
        }

        return employmentVerificationDtoList;

    }

    private boolean validateCandidateStatus(Long candidateId) {
        Set<CandidateStatusEnum> candidateStatusEnums = candidateStatusHistoryRepository.findAllByCandidateCandidateId(
                candidateId).stream().map(candidateStatusHistory ->
                CandidateStatusEnum.valueOf(candidateStatusHistory.getStatusMaster().getStatusCode())).collect(Collectors.toSet());
        // return (candidateStatusEnums.contains(CandidateStatusEnum.EPFO) && candidateStatusEnums.contains(CandidateStatusEnum.ITR));
        return (candidateStatusEnums.contains(CandidateStatusEnum.ITR));
    }


}
