/*-
 *   Copyright (C) 2021 Presidenza del Consiglio dei Ministri.
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
package it.interop.dgc.gateway.worker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bouncycastle.cms.CMSException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.interop.dgc.gateway.akamai.AkamaiFastPurge;
import it.interop.dgc.gateway.client.RestApiClient;
import it.interop.dgc.gateway.client.base.RestApiException;
import it.interop.dgc.gateway.client.base.RestApiResponse;
import it.interop.dgc.gateway.dto.TrustListItemDto;
import it.interop.dgc.gateway.entity.DgcLogEntity;
import it.interop.dgc.gateway.entity.DgcLogEntity.OperationType;
import it.interop.dgc.gateway.entity.DgcLogInfo;
import it.interop.dgc.gateway.entity.SignerInformationEntity;
import it.interop.dgc.gateway.entity.SignerInvalidInformationEntity;
import it.interop.dgc.gateway.entity.SignerUploadInformationEntity;
import it.interop.dgc.gateway.enums.CertificateType;
import it.interop.dgc.gateway.mapper.DgcMapper;
import it.interop.dgc.gateway.repository.DgcLogRepository;
import it.interop.dgc.gateway.repository.SignerInformationRepository;
import it.interop.dgc.gateway.repository.SignerInvalidInformationRepository;
import it.interop.dgc.gateway.repository.SignerUploadInformationRepository;
import it.interop.dgc.gateway.signing.CertificateSignatureException;
import it.interop.dgc.gateway.signing.CertificateSignatureVerifier;
import it.interop.dgc.gateway.signing.SignatureService;
import it.interop.dgc.gateway.util.DscUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Slf4j
@Service
public class DgcWorker {

	@Getter
	@Value("${dgc.origin_country}")
	private String originCountry;
	
	@Autowired(required=true)
	private RestApiClient client;

	@Autowired(required=true)
	private SignerUploadInformationRepository signerUploadInformationRepository;

	@Autowired(required=true)
	private SignerInvalidInformationRepository signerInvalidInformationRepository;
	
	@Autowired(required=true)
	private SignerInformationRepository signerInformationRepository;

	@Autowired(required=true)
	private DgcLogRepository dgcLogRepository;

	@Autowired(required=true)
	private SignatureService signatureService;

	@Autowired(required=true)
	private CertificateSignatureVerifier signatureVerifier;
	
	@Autowired(required=true)
	private AkamaiFastPurge akamaiFastPurge;
	
	@Scheduled(cron = "${dgc.worker.upload.schedul}")
	@SchedulerLock(name = "DgcWorker_uploadWorker")
	public void uploadWorker() {
		log.info("@@@  UPLOAD -> START Processing upload. @@@");

		List<SignerUploadInformationEntity> toSendSignerInformationList = signerUploadInformationRepository.getSignerInformationToSend();
		if (toSendSignerInformationList != null) {
			for (SignerUploadInformationEntity signerInformation:toSendSignerInformationList) {
				send(signerInformation);
			}
			
		}
		
		List<SignerUploadInformationEntity> toRevokeSignerInformationList = signerUploadInformationRepository.getSignerInformationToRevoke();
		if (toRevokeSignerInformationList != null) {
			for (SignerUploadInformationEntity signerInformation:toRevokeSignerInformationList) {
				revoke(signerInformation);
			}
			
		}

		log.info("@@@  UPLOAD -> END Processing upload. @@@");
	}

	
	@Scheduled(cron = "${dgc.worker.download.schedul}")
	@SchedulerLock(name = "DgcWorker_downloadWorker")
	public void downloadWorker() {
		log.info("###  DOWNLOAD -> START Processing download. ###");
		download();
		log.info("###  DOWNLOAD -> END Processing download. ####");
	}


	
	
	@Transactional
	private String send(SignerUploadInformationEntity signerInformationEntity) {
		String report = null;
		String batchTag = DscUtil.batchTagGenerator(OperationType.UPLOAD);

		try {
			
			if (signerInformationEntity != null) {
				String signedCertificate = signatureService.getSignatureForBytes(signerInformationEntity.getRawData());
				RestApiResponse<String> resp = client.postVerificationInformation(signedCertificate, originCountry);
				report = resp.getStatusCode().toString();
				
				if (resp.getStatusCode() == RestApiClient.DOWNLOAD_STATUS_RETURNS_BATCH_200) {
					signerInformationEntity.setUploadBatchTag(batchTag);
					signerUploadInformationRepository.save(signerInformationEntity);
				}

			}
			
		} catch (RestApiException | CMSException | IOException | CertificateSignatureException e) {
			report = e.getMessage();
			log.error("ERROR Processing upload RestApiException. -> batchTag: {} ", batchTag, e);
		}
		log.info("Upload INFO after sending -> batchTag: {} ", batchTag);
		
		dgcLogRepository.save(DgcLogEntity.buildUploadDgcLog(originCountry, batchTag, report));
		
		return report;
	}
	
	@Transactional
	private String revoke(SignerUploadInformationEntity signerInformationEntity) {
		String report = null;
		String batchTag = DscUtil.batchTagGenerator(OperationType.REVOKE);

		try {
			
			if (signerInformationEntity != null) {
				String signedCertificate = signatureService.getSignatureForBytes(signerInformationEntity.getRawData());
				RestApiResponse<String> resp = client.revokeVerificationInformation(signedCertificate, originCountry);
				report = resp.getStatusCode().toString();
				
				if (resp.getStatusCode() == RestApiClient.DOWNLOAD_STATUS_RETURNS_BATCH_200) {
					signerInformationEntity.setRevokedBatchTag(batchTag);
					signerUploadInformationRepository.save(signerInformationEntity);
				}

			}
			
		} catch (RestApiException | CMSException | IOException | CertificateSignatureException e) {
			report = e.getMessage();
			log.error("ERROR Processing upload RestApiException. -> batchTag: {} ", batchTag, e);
		}
		log.info("Upload INFO after sending -> batchTag: {} ", batchTag);
		
		dgcLogRepository.save(DgcLogEntity.buildRevokeDgcLog(originCountry, batchTag, report));
		
		return report;
	}
	
	
	@Transactional
	private void download() {
		String report = null;
		
		String batchTag = DscUtil.batchTagGenerator(OperationType.DOWNLOAD);
		DgcLogInfo dgcLogInfo = new DgcLogInfo();
		
		try {
			RestApiResponse<List<TrustListItemDto>> resp = client.downloadTrustList();
			report = resp.getStatusCode().toString();
			
			if (resp.getStatusCode() == RestApiClient.DOWNLOAD_STATUS_RETURNS_BATCH_200) {
				
				if (resp.getData() != null) {
					//Revoco tutti icertificati
					Integer numNewDoc = 0;
					Integer numInvalidDoc = 0;
					Integer numOldDoc = 0;
					Integer numTotDoc = signerInformationRepository.setAllTrustedPartyRevoked(batchTag);
					List<TrustListItemDto> trustList = resp.getData();
					
					List<TrustListItemDto> trustListCsca = trustList.stream()
							.filter(csca -> csca.getCertificateType() == CertificateType.CSCA)
							.collect(Collectors.toList());

					if (trustListCsca != null && trustListCsca.size() > 0) {
						trustListCsca.forEach(csca -> {
							csca.setVerifiedSign(signatureVerifier.checkTrustAnchorSignature(csca));
						});
						
						List<TrustListItemDto> trustListDsc = trustList.stream()
								.filter(dsc -> dsc.getCertificateType() == CertificateType.DSC)
								.collect(Collectors.toList());
						
						if (trustListDsc != null && trustListDsc.size() > 0) {
							trustListDsc.forEach(dsc-> {
								dsc.setVerifiedSign(false);
								List<TrustListItemDto> trustListCscaCountry = trustListCsca.stream()
										.filter(csca -> csca.getCountry().equals(dsc.getCountry()) && csca.isVerifiedSign())
										.collect(Collectors.toList());
								if (trustListCscaCountry != null && trustListCscaCountry.size() > 0) {
									for (TrustListItemDto csca:trustListCscaCountry) {
										boolean isVerified = signatureVerifier.trustListItemSignedByCa(dsc, csca);
										dsc.setVerifiedSign(isVerified);
										if (isVerified) {
											break;
										}
									}
								}
							});
						}

						dgcLogInfo.setNumTotDoc(numTotDoc);
						dgcLogInfo.setNumDocFlusso(trustList.size());
						
						Long index = signerInformationRepository.maxIndex();
						
						for (TrustListItemDto trustListItemDto:trustList) {
							SignerInformationEntity trustedPartyEntity = signerInformationRepository.getByKid(trustListItemDto.getKid());
							if (trustedPartyEntity!=null) {
								//I certificati già presenti nel DB vengono riabilitati
								trustedPartyEntity.setRevoked(false);
								trustedPartyEntity.setRevokedDate(null);
								trustedPartyEntity.setRevokedBatchTag(null);
								signerInformationRepository.save(trustedPartyEntity);
								numOldDoc++;
							} else {
								//I certificati non presenti nel DB vengono inseriti e flaggati da pubblicare
								if (trustListItemDto.isVerifiedSign()) {
									trustedPartyEntity = DgcMapper.trustListDtoToEntity(trustListItemDto);
									trustedPartyEntity.setDownloadBatchTag(batchTag);
									trustedPartyEntity.setIndex(++index);
									signerInformationRepository.save(trustedPartyEntity);
								} else {
									SignerInvalidInformationEntity signerInvalidInformationEntity = DgcMapper.invalidTrustListDtoToEntity(trustListItemDto);
									signerInvalidInformationEntity.setDownloadBatchTag(batchTag);
									signerInvalidInformationRepository.save(signerInvalidInformationEntity);
									numInvalidDoc++;
								}
								numNewDoc++;
							}
						}
						dgcLogInfo.setNumInvalidDoc(numInvalidDoc);
						dgcLogInfo.setNumRevokedDoc(numTotDoc - numNewDoc - numOldDoc - numInvalidDoc);
						
					}
					
				}
				
				akamaiFastPurge();
				
			}

		} catch (RestApiException e) {
			report = e.getMessage();
			log.error("ERROR Processing download RestApiException. -> batchTag: {} ", batchTag, e);
		}
		log.info("Download INFO after reciving -> batchTag: {} ", batchTag);

		dgcLogRepository.save(DgcLogEntity.buildDownloadDgcLog(originCountry, batchTag, dgcLogInfo, report));
		
		
		
	}
	
	
	private boolean akamaiFastPurge() {
		List arrayList = new ArrayList<String>();
		arrayList.add("*****-Actual-url-you-want-to-purge*****");
		return akamaiFastPurge.invalidateUrls(arrayList);
	}
	
	
	
}