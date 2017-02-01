package eu.cloudopting.bpmn.tasks.deploy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

import eu.cloudopting.tosca.ToscaService;

@Service
public class DeploySetup implements JavaDelegate {
	private final Logger log = LoggerFactory.getLogger(DeploySetup.class);
	@Autowired
	ToscaService toscaService;
	
	@Value("${cloud.doDeploy}")
	private boolean doDeploy;
	private String publicKey;
	private String privateKey;
	
	@Override
	public void execute(DelegateExecution execution) throws Exception {
		// TODO Auto-generated method stub
		log.info("in DeploySetup");
		String customizationId = (String) execution.getVariable("customizationId");
		String organizationName = (String) execution.getVariable("organizationName");
		//TODO for Luca need a method to get the passphrase from TOSCA
		String passphrase = "";
		String service = toscaService.getServiceName(customizationId);
		log.debug("service: "+service);
		String coRoot = new String("/cloudOptingData");
		String serviceHome = new String(coRoot+"/"+organizationName+"-"+service);
		log.debug("serviceHome: "+serviceHome);
		
		boolean success = false;
		File directory = new File(serviceHome);
		if (directory.exists()) {
			System.out.println("Directory already exists ...");
			FileUtils.cleanDirectory(directory);

		} else {
			System.out.println("Directory not exists, creating now");

			success = directory.mkdir();
			if (success) {
				System.out.printf("Successfully created new directory : %s%n",
						serviceHome);
			} else {
				System.out.printf("Failed to create new directory: %s%n", serviceHome);
			}
		}
		
		toscaService.generatePuppetfile(customizationId, serviceHome);
		
		ArrayList<String> dockerPortsList = toscaService.getHostPorts(customizationId);
		dockerPortsList.add("Port1");
		
		ArrayList<String> dockerNodesList = toscaService.getArrNodesByType(customizationId, "DockerContainer");
		ArrayList<String> dockerDataVolumeNodesList = toscaService.getArrNodesByType(customizationId, "DockerDataVolumeContainer");
		
		//TODO for Davide create the keys for SSH
		
		String RSApassphrase = "foo"; //we should find a way to get this password from the user
		createRSAKeys(RSApassphrase );

		execution.setVariable("publickey", publicKey);
		execution.setVariable("privatekey", privateKey); 
		
		
		log.debug("dockerNodesList");
		log.debug(dockerNodesList.toString());
		log.debug("dockerPortsList");
		log.debug(dockerPortsList.toString());
		execution.setVariable("dockerNodesList", dockerNodesList);
		execution.setVariable("dockerDataVolumeNodesList", dockerDataVolumeNodesList);
		execution.setVariable("vmPortsList", dockerPortsList);
		
		log.debug("organizationName:"+organizationName);
		log.debug("service:"+service);
		log.debug("coRoot:"+coRoot);
		log.debug("serviceHome:"+serviceHome);
		// setting the variables for the rest of the tasks
		execution.setVariable("customizationName", organizationName+"-"+service);
		execution.setVariable("coRoot", coRoot);
		execution.setVariable("service", service);
		execution.setVariable("serviceHome", serviceHome);

		
	}
	
	private void createRSAKeys(String passphrase) throws JSchException, FileNotFoundException, IOException {
		JSch jsch = new JSch();

		KeyPair kpair;

		kpair = KeyPair.genKeyPair(jsch, KeyPair.RSA);

		kpair.writePrivateKey("/cloudOptingData/private.key", passphrase.getBytes());
		kpair.writePublicKey("/cloudOptingData/public.key", "");
		
		System.out.println("Finger print: " + kpair.getFingerPrint());
		kpair.dispose();
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		kpair.writePublicKey(out, "");
		publicKey = out.toString("UTF-8");
		
		out = new ByteArrayOutputStream();
		kpair.writePrivateKey(out, passphrase.getBytes());
		privateKey = out.toString("UTF-8");

	}

}
