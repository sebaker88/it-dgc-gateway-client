/*-
 *   Copyright (C) 2021 Ministero della Salute and all other contributors.
 *   Please refer to the AUTHORS file for more information.
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as
 *   published by the Free Software Foundation, either version 3 of the
 *   License, or (at your option) any later version.
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *   GNU Affero General Public License for more details.
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package it.interop.dgc.gateway.controller;

import it.interop.dgc.gateway.akamai.AkamaiFastPurge;
import it.interop.dgc.gateway.entity.SignerUploadInformationEntity;
import it.interop.dgc.gateway.repository.SignerUploadInformationRepository;
import it.interop.dgc.gateway.signing.SignatureService;
import it.interop.dgc.gateway.worker.DgcWorker;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class TestController {

    @Autowired(required = true)
    private SignerUploadInformationRepository signerUploadInformationRepository;

    @Autowired(required = true)
    private SignatureService signatureService;

    @Autowired(required = true)
    private DgcWorker efgsWorker;

    @Autowired(required = true)
    private AkamaiFastPurge akamaiFastPurge;

    @GetMapping("/testUpload")
    public ResponseEntity<String> testUpload() {
        StringBuffer content = new StringBuffer();
        try {
            efgsWorker.uploadWorker();
            log.info("OK");
            content.append("testUpload: OK");
        } catch (Exception e) {
            e.printStackTrace();
            content.append("Errore: ").append(e.getMessage()).append("<br>");
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }

    @GetMapping("/testDownload")
    public ResponseEntity<String> testDownload() {
        StringBuffer content = new StringBuffer();
        try {
            efgsWorker.downloadWorker();
            log.info("OK");
            content.append("testDownload: OK");
        } catch (Exception e) {
            e.printStackTrace();
            content.append("Errore: ").append(e.getMessage()).append("<br>");
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }

    @GetMapping("/testSign")
    public ResponseEntity<String> testSign() {
        StringBuffer content = new StringBuffer();
        try {
            List<SignerUploadInformationEntity> toSendSignerInformationList = signerUploadInformationRepository.getSignerInformationToSend();

            if (
                toSendSignerInformationList != null &&
                toSendSignerInformationList.size() > 0
            ) {
                log.info(
                    "raw data : {}",
                    toSendSignerInformationList.get(0).getRawData()
                );
                String signedCertificate = signatureService.getSignatureForBytes(
                    toSendSignerInformationList.get(0).getRawData()
                );
                log.info("signed raw data : {}", signedCertificate);
                content.append("testSign: OK").append("<br>");
                content
                    .append("raw data : ")
                    .append(toSendSignerInformationList.get(0).getRawData())
                    .append("<br>");
                content
                    .append("signed raw data : ")
                    .append(signedCertificate)
                    .append("<br>");
            }

            log.info("OK");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            content.append("Errore: ").append(e.getMessage()).append("<br>");
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }

    @GetMapping("/testAkamai")
    public ResponseEntity<String> testAkamai() {
        StringBuffer content = new StringBuffer();
        try {
            if (
                akamaiFastPurge.getUrl() != null &&
                !"".equals(akamaiFastPurge.getUrl())
            ) {
                String akamaiReport = akamaiFastPurge.invalidateUrls();
                content.append("testAkamai: ").append(akamaiReport);
            }
        } catch (Exception e) {
            content.append("ERROR INVALIDATING AKAMAI CACHE");
            log.error("ERROR Invalidating akamai cache. ->  ", e);
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }

    @GetMapping("/testRuleAkamai")
    public ResponseEntity<String> testRuleAkamai() {
        StringBuffer content = new StringBuffer();
        try {
            if (
                akamaiFastPurge.getUrl() != null &&
                !"".equals(akamaiFastPurge.getUrl())
            ) {
                String akamaiReport = akamaiFastPurge.invalidateRulesUrls();
                content.append("testAkamai: ").append(akamaiReport);
            }
        } catch (Exception e) {
            content.append("ERROR INVALIDATING AKAMAI CACHE");
            log.error("ERROR Invalidating akamai cache. ->  ", e);
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }

    //BUSINESS RULE
    @GetMapping("/testCountryDownload")
    public ResponseEntity<String> testCountryDownload() {
        StringBuffer content = new StringBuffer();
        try {
            efgsWorker.downloadCountry();
            log.info("OK");
            content.append("testCountryDownload: OK");
        } catch (Exception e) {
            e.printStackTrace();
            content.append("Errore: ").append(e.getMessage()).append("<br>");
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }

    @GetMapping("/testValuesDownload")
    public ResponseEntity<String> testValuesDownload() {
        StringBuffer content = new StringBuffer();
        try {
            efgsWorker.downloadValues();
            log.info("OK");
            content.append("testValuesDownload: OK");
        } catch (Exception e) {
            e.printStackTrace();
            content.append("Errore: ").append(e.getMessage()).append("<br>");
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }

    @GetMapping("/testBusinessRuleDownload")
    public ResponseEntity<String> testBusinessRuleDownload() {
        StringBuffer content = new StringBuffer();
        try {
            efgsWorker.downloadRules();
            log.info("OK");
            content.append("testValuesDownload: OK");
        } catch (Exception e) {
            e.printStackTrace();
            content.append("Errore: ").append(e.getMessage()).append("<br>");
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }
    
    @GetMapping("/testRevocheDownload")
    public ResponseEntity<String> testRevocheDownload() {
        StringBuffer content = new StringBuffer();
        try {
            efgsWorker.downloadRevoche();
            log.info("OK");
            content.append("testValuesDownload: OK");
        } catch (Exception e) {
            e.printStackTrace();
            content.append("Errore: ").append(e.getMessage()).append("<br>");
        }
        return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
    }

	@GetMapping("/testRevocheDownloadBatch")
	public ResponseEntity<String> testRevocheDownloadBatch() {
		StringBuffer content = new StringBuffer();
		try {
			efgsWorker.downloadBatch("bfa7f2f0-43c9-4881-9270-7b8b44228f3a");
			log.info("OK");
			content.append("testRevocheDownloadBatch: OK");
		} catch (Exception e) {
			e.printStackTrace();
			content.append("Errore: ").append(e.getMessage()).append("<br>");
		}
		return new ResponseEntity<String>(content.toString(), HttpStatus.OK);
	}
}

    
