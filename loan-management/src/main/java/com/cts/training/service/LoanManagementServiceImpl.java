package com.cts.training.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

import com.cts.training.exception.CollateralTypeNotFoundException;
import com.cts.training.exception.CustomerLoanNotFoundException;
import com.cts.training.exception.LoanNotFoundException;
import com.cts.training.feign.CollateralFeign;
import com.cts.training.model.CustomerLoan;
import com.cts.training.model.Loan;
import com.cts.training.model.LoanApplication;
import com.cts.training.pojo.CashDeposit;
import com.cts.training.pojo.RealEstate;
import com.cts.training.repo.CustomerLoanRepo;
import com.cts.training.repo.LoanApplicationRepo;
import com.cts.training.repo.LoanRepo;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

/**
 * LoanManagementService implementation
 */
@Service
@Slf4j
public class LoanManagementServiceImpl implements LoanManagementService {

	@Autowired
	private CollateralFeign client;

	@Autowired
	private CustomerLoanRepo customerLoanRepo;

	@Autowired
	private LoanRepo loanRepo;
	
	@Autowired
	private LoanApplicationRepo loanApplicationRepo;

	private static final String MESSAGE = "Customer Loan Not found with LoanId: ";

	
	/**
	 * Get Loan Details Implimentation
	 */
	@Override
	public CustomerLoan getLoanDetails(int loanId, int customerId) throws CustomerLoanNotFoundException {
		log.info("Get Loan details using loan id and customer id");
		log.info(loanId+"======="+customerId);
		System.out.println("Inside loan management service================");
		CustomerLoan customerLoan = customerLoanRepo.findById(loanId)
		.orElseThrow(() -> new CustomerLoanNotFoundException(MESSAGE + loanId));
		/*
		 * Optional<CustomerLoan> customerLoan=customerLoanRepo.findById(loanId);
		 * System.out.println(customerLoan.get()); if(!customerLoan.isPresent()) { throw
		 * new CustomerLoanNotFoundException(MESSAGE+loanId); }
		 */
		System.out.println(customerLoan);
		if (customerLoan.getCustomerId() != customerId) {
			throw new CustomerLoanNotFoundException(MESSAGE + loanId);
		}
		return customerLoan;
	}
	
	/**
	 * Save RealEstate Implementatiom
	 * 
	 * @throws LoanNotFoundException
	 */
	@Override
	public ResponseEntity<String> saveRealEstate(String token, RealEstate realEstate)
			throws CustomerLoanNotFoundException, LoanNotFoundException {
		log.info("Save Real Estate collateral details");
		System.out.println("===========Saving Real Estate details============= from loan management service"+realEstate);
		CustomerLoan customerLoan = customerLoanRepo.findById(realEstate.getLoanId())
				.orElseThrow(() -> new CustomerLoanNotFoundException(MESSAGE + realEstate.getLoanId()));

		Integer prodId = customerLoan.getLoanProductId();
		Optional<Loan> loanop = loanRepo.findById(prodId);
		if(!loanop.isPresent()){
			throw new LoanNotFoundException("Loan Not found by Id" + prodId);
		}else{
			Loan loan = loanop.get();
			String type = loan.getCollateralType();
		try {
			if (type.equals("REAL_ESTATE")) {

				customerLoan.setCollateralId(realEstate.getCollateralId());
				customerLoanRepo.save(customerLoan);
				return client.saveRealEstateCollateral(token, realEstate);
			} else {
				throw new CollateralTypeNotFoundException("Collateral Mismatch");
			}
		} catch (FeignException e) {
			e.printStackTrace();
			throw new CollateralTypeNotFoundException("Collateral already exists with loan id");
		}
		}
	}
	
	/**
	 * Save Cash Deposit Implementation
	 * 
	 * @throws LoanNotFoundException
	 */
	@Override
	public ResponseEntity<String> saveCashDeposit(String token, CashDeposit cashDeposit)
			throws CustomerLoanNotFoundException, LoanNotFoundException {
		log.info("Save Cash Deposit collateral details");
		
		CustomerLoan customerLoan = customerLoanRepo.findById(cashDeposit.getLoanId())
				.orElseThrow(() -> new CustomerLoanNotFoundException(MESSAGE + cashDeposit.getLoanId()));

		Integer prodId = customerLoan.getLoanProductId();
		Optional<Loan> loanop = loanRepo.findById(prodId);
		if(!loanop.isPresent()){
			throw new LoanNotFoundException("Loan not Found By Id:" + prodId);
		}else{
			Loan loan = loanop.get();
			String type = loan.getCollateralType();
			try {
				if (type.equals("CASH_DEPOSIT")) {
					customerLoan.setCollateralId(cashDeposit.getCollateralId());
					customerLoanRepo.save(customerLoan);
					return client.saveCashDepositCollateral(token, cashDeposit);
				} else {
					throw new CollateralTypeNotFoundException("Collateral Mismatch");
				}
			} catch (FeignException e) {
				
				throw new CollateralTypeNotFoundException("Collateral already exists with loan id");
			}
		}
	}

	@Override
	public ResponseEntity<String> applyLoan(LoanApplication loanApplication) {
		System.out.println(loanApplicationRepo.save(loanApplication));
		return new ResponseEntity<>("Application Saved", HttpStatus.ACCEPTED);
	}
	
	@Override
	public ArrayList<LoanApplication> viewCustLoan(int custId) {
		ArrayList<LoanApplication> list=new ArrayList<>();
		System.out.println("Inside loan-management service "+custId);
		for(LoanApplication app:loanApplicationRepo.findAll()) {
			System.out.println("value :"+app);
			if(app.getCustomerId()==custId) {
				
				list.add(app);
			}
		}
		
		return list;
	}
	
	@Override
	public ArrayList<LoanApplication> getAll(){
		ArrayList<LoanApplication> list=new ArrayList<LoanApplication>();
		for(LoanApplication app:loanApplicationRepo.findAll()) {
			if(!app.getStatus().equals("Accepted") && !app.getStatus().equals("Rejected"))
				list.add(app);
		}
		return list;
	}
	@Override
	public ResponseEntity<String> approveLoan(Integer applicationId){
		
		LoanApplication app= loanApplicationRepo.findById(applicationId).get();
		
//		loanApplicationRepo.delete(app);
		app.setStatus("Accepted");
		loanApplicationRepo.save(app);
		
		
		CustomerLoan cLoan=new CustomerLoan();
		Integer cId=0;
		if(app.getCollateralDetails().equalsIgnoreCase("Cash Deposit")) {
			cId=101;
		}
		else if(app.getCollateralDetails().equalsIgnoreCase("Real Estate")) {
			cId=102;
		}
		Double emi=(Double)app.getLoanAmount()/12.0*app.getTenure();
		cLoan.setCustomerId(app.getCustomerId());
		cLoan.setLoanPrincipal(app.getLoanAmount());
		cLoan.setTenure(app.getTenure());
		cLoan.setInterest(12.5);
		cLoan.setEmi(emi);
		cLoan.setCollateralId(cId);
		System.out.println(customerLoanRepo.save(cLoan));
		cLoan.setLoanProductId(cLoan.getLoanId()+1000);
		System.out.println(customerLoanRepo.save(cLoan));
		
		
		return new ResponseEntity<>("Loan Application Accepted", HttpStatus.ACCEPTED);
	
	}
	@Override
	public ResponseEntity<String> rejectLoan(Integer applicationId){
		
		LoanApplication app=loanApplicationRepo.findById(applicationId).get();
//		loanApplicationRepo.delete(app);
		app.setStatus("Rejected");
		loanApplicationRepo.save(app);
		return new ResponseEntity<>("Loan Application Rejected", HttpStatus.ACCEPTED);
	
	}
}
