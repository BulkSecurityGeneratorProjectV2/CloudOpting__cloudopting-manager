package eu.cloudopting.bpmn.tasks.publish;

import eu.cloudopting.domain.Applications;
import eu.cloudopting.domain.Status;
import eu.cloudopting.dto.ApplicationDTO;
import eu.cloudopting.service.ApplicationService;
import eu.cloudopting.service.StatusService;
import eu.cloudopting.service.util.StatusConstants;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.transaction.Transactional;

@Service
@Transactional
public class PublishContextSetupTask implements JavaDelegate {
    private final Logger log = LoggerFactory.getLogger(PublishContextSetupTask.class);
    //	@Autowired
//	ToscaService toscaService;
    @Inject
    private StatusService statusService;

    @Inject
    private ApplicationService applicationService;



    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Publish - Context SetUp");
        ApplicationDTO applications = (ApplicationDTO) execution.getVariable("application");
        Applications application = new Applications();
        BeanUtils.copyProperties(applications,application);
        Status status = statusService.findOne(StatusConstants.DRAFT);
        application.setStatusId(status);
        application.setApplicationVersion(String.valueOf(1));
        applicationService.create(application);
    }

}
