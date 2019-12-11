package group36;

import java.util.ArrayList;
import java.util.List;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils.Collections;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.User;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

/**
 * A simple example agent that makes random bids above a minimum target utility. 
 *
 * @author Tim Baarslag
 */
public class Agent36 extends AbstractNegotiationParty 
{
	private static double MINIMUM_TARGET = 0.8;
	// Variables for modeling opponent concession 
	private static double INITIAL_T = 0.05;
	private static double BETA = 5;
	
	private double coeff = 0;
	
	private Bid lastOffer;

	// Variables for opponent modeling 
	private List<List<Integer>> FreqTable1 = new ArrayList<List<Integer>>();
	private List<List<Integer>> FreqTable2 = new ArrayList<List<Integer>>();
	private List<List<Double>> CalcTable = new ArrayList<List<Double>>();
	private List<Double> Weights = new ArrayList<Double>();
	private List<Double> NormWeights = new ArrayList<Double>();
	private List<Integer> ValueIndex = new ArrayList<Integer>();
	private int NoOfBids;
	
	private List<Double> opponentUtility = new ArrayList<Double>();
	private List<Double> opponentAvgUtility = new ArrayList<Double>();
	
	double t = INITIAL_T;		// Threshold for accepting/proposing bids in the bids ranking. 0.2 = Top 20% bids

	
	/**
	 * Initializes a new instance of the agent.
	 */
	@Override
	public void init(NegotiationInfo info) 
	{
		super.init(info);
		
		if (hasPreferenceUncertainty()) {
			System.out.println("Preference uncertainty is enabled.");
			System.out.println("Agent ID: " + info.getAgentID().toString());
			System.out.println("No. of possible bids: " + userModel.getDomain().getNumberOfPossibleBids());
			System.out.println("No. of bids in the preference ranking: " + userModel.getBidRanking().getSize());
			System.out.println("Elicitation Cost: " + info.getUser().getElicitationCost());
			System.out.println("Bid with lowest utility: " + userModel.getBidRanking().getMinimalBid() + "( " + getUtility(userModel.getBidRanking().getMinimalBid()) + " )");
			System.out.println("Bid with highest utility: " + userModel.getBidRanking().getMaximalBid()  + "( " + getUtility(userModel.getBidRanking().getMaximalBid()) + " )");
			System.out.println("5th bid in the ranking list: " + userModel.getBidRanking().getBidOrder().get(135) + "( " + getUtility(userModel.getBidRanking().getBidOrder().get(135)) + " )");
		}
		
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

		List< Issue > issues = additiveUtilitySpace.getDomain().getIssues();
		//Bid TestBid = null;
		
		for (Issue issue : issues) {
			
			// For each issue create a new row in the frequency table
			List<Integer> issuerow1 = new ArrayList<Integer>();
			List<Integer> issuerow2 = new ArrayList<Integer>();
			List<Double> issuerow3 = new ArrayList<Double>();
			
		    int issueNumber = issue.getNumber();
		    System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));

		    // Assuming that issues are discrete only
		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		    	
		    	// For each discrete value add a new column in the issue row
		    	issuerow1.add(0);
		    	issuerow2.add(0);
		    	issuerow3.add(0.0);
		    	
		    	System.out.println(valueDiscrete.getValue());
		        System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
		        try {
		            System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
		        } catch (Exception e) {
		            e.printStackTrace();
		        } 
		    }
		    
		    // C3P0 START - Add the issue row to the frequency table
		    FreqTable1.add(issuerow1);
		    FreqTable2.add(issuerow2);
		    CalcTable.add(issuerow3);
		    Weights.add(0.0);
		    NormWeights.add(0.0);
		    ValueIndex.add(0);
		    NoOfBids = 0;
		    // C3P0 END
		}	
	}
	
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) 
	{
		// Check for acceptance if we have received an offer
		int rankingSize = userModel.getBidRanking().getSize();
		int t_index = (int)(rankingSize * (1.0 - t));	// Index to separate bids above the threshold
		List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();	// Bids ranking
		List<Bid> bidOrderT = bidOrder.subList(t_index,rankingSize);	// Bids ranking above threshold
		
		if (lastOffer != null) {
			if(!(bidOrder.contains(lastOffer))) {
				userModel = user.elicitRank(lastOffer, userModel);
				// Update the bid ranking variables after eliciting a bid
				rankingSize = userModel.getBidRanking().getSize();
				System.out.println("Updating rankings. New size: " + rankingSize);
				t_index = (int)(rankingSize * (1.0 - t));
				bidOrder = userModel.getBidRanking().getBidOrder();
				bidOrderT = bidOrder.subList(t_index,rankingSize);
			}
			
			System.out.println("Threshold ranking index: " + t_index);
			System.out.println("Bid ranking: " + (rankingSize - bidOrder.indexOf(lastOffer)));
			
			// Change the acceptance threshold as a function of time and the opponent concession coefficient 
			t = INITIAL_T + Math.pow(getTimeLine().getTime(), BETA) * ((getUtility(userModel.getBidRanking().getMaximalBid()) - getUtility(userModel.getBidRanking().getMinimalBid()))/2 - INITIAL_T)
					      + coeff;
			
			System.out.println("COEFF: " + coeff + " @" + timeline.getTime());
			
			System.out.println("Updating T: " + t);		
			
			if(bidOrderT.contains(lastOffer)) {
				System.out.println("Accepted bid present in the thresh. ranking.");
				System.out.println("Ranking: " + (rankingSize - bidOrder.indexOf(lastOffer)));
				System.out.println("Threshold Ranking: " + bidOrderT);
				
				return new Accept(getPartyId(), lastOffer);			
			}
			
			if (timeline.getTime() >= 0.95) {
				if (getUtility(lastOffer) >= utilitySpace.getReservationValue()) {
					System.out.println("Accepted (any) bid with utility: " + getUtility(lastOffer));
					return new Accept(getPartyId(), lastOffer);} // G: Accept any offer at the end of the timeline
				else {	// G: If the offer is lower than the Reserv. Value then End Negotiation
					System.out.println("C3P0 ended negotiation at time: " + timeline.getTime() + ", bid lower than reserv. value: " + utilitySpace.getReservationValue());
					return new EndNegotiation(getPartyId());
				}
			}
		}
		// Otherwise, send out a random offer above the target utility 
		List<Bid> rBids = generateRandomBidsRanked(bidOrderT);
		double OpponentUtility;
		double WinningOpUtility = 0.0;
		Bid WinningBid = generateRandomBidAboveTarget();
		
		//Iterate over each of the random bids, for each bid get the opponent utility. 
		//At the end, the bid with the highest opponent utility will be stored in WinningBid and will be offered
		for (Bid thisBid : rBids) {
			OpponentUtility = getOpponentUtility(thisBid);
			if (OpponentUtility > WinningOpUtility) {
				WinningOpUtility = OpponentUtility;
				WinningBid = thisBid;
				//System.out.println("New winning utility!: " + WinningOpUtility);
			}
		}
		System.out.println("Offering bid with ranking: " + (rankingSize - bidOrder.indexOf(WinningBid)));
		
		return new Offer(getPartyId(), WinningBid);
	}
	
	private int updateRankings(double t, List<Bid> bidOrder, List<Bid> bidOrderT) {
		int t_index = 0;
		int rankingSize = 0;
		rankingSize = userModel.getBidRanking().getSize();
		System.out.println("Updating rankings. New size: " + rankingSize);
		t_index = (int)(rankingSize * (1.0 - t));
		bidOrder = userModel.getBidRanking().getBidOrder();
		bidOrderT = bidOrder.subList(t_index,rankingSize);
		return rankingSize;
	}
	
	// Gets the bid with maximum utility
	private Bid getMaxUtilityBid() {
	    try {
	        return utilitySpace.getMaxUtilityBid();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return null;
	}
	
	// Gets the bid with minimum utility
	private Bid getMinUtilityBid() {
	    try {
	        return utilitySpace.getMinUtilityBid();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return null;
	}
	
	private Bid generateRandomBidAboveTarget() 
	{
		Bid randomBid;
		double util;
		int i = 0;
		// try 100 times to find a bid under the target utility
		do 
		{	
			randomBid = generateRandomBid();
			util = utilitySpace.getUtility(randomBid);
		} 
		while (util < MINIMUM_TARGET && i++ < 100);		
		return randomBid;
	}

	// Instead of generating a single random bid, generate an array of them
	private List<Bid> generateRandomBids() 
	{
		List<Bid> rBids = new ArrayList<Bid>();
		Bid randomBid;
		
		double util;
		int i = 0;
		// try N times to find bids under the target utility
		do 
		{	
			randomBid = generateRandomBid();
			util = utilitySpace.getUtility(randomBid);
			if (util > MINIMUM_TARGET) {
				rBids.add(randomBid);
			}
		} 
		while (i++ < 1000);		
		return rBids;
	}
	
	// Generate an array of random bids that meet the threshold ranking
	private List<Bid> generateRandomBidsRanked(List<Bid> bidOrderT) 
	{
		List<Bid> rBids = new ArrayList<Bid>();
		Bid randomBid;

		int i = 0;
		// try N times to find bids under the threshold ranking
		do 
		{	
			randomBid = generateRandomBid();
			if(bidOrderT.contains(randomBid)) {
				if(!(rBids.contains(randomBid))) {
					rBids.add(randomBid);
				}
			}
		} 
		while (i++ < 1000);		
		return rBids;
	}
	
	public double calculateCoeff()
	{
		double coeff = 0.0;
		if ((timeline.getTime() >= 0.05) && (timeline.getTime() <= 0.45))
			if (opponentAvgUtility.size() > 1)
				coeff = opponentAvgUtility.get(opponentAvgUtility.size() - 2) - opponentAvgUtility.get(opponentAvgUtility.size() - 1);
		return coeff;
	}
	
	
	/**
	 * Remembers the offers received by the opponent.
	 */
	@Override
	public void receiveMessage(AgentID sender, Action action) 
	{
		double utility = 0.0;
		double avgUtil = 0.0;

		if (action instanceof Offer) 
		{
			lastOffer = ((Offer) action).getBid();
			// Update the frequency and weight tables and get the opponent utility 
			NoOfBids = NoOfBids + 1;
			updateFreqTable(lastOffer);
			updateWeightsArray(lastOffer);
			utility = getOpponentUtility(lastOffer);
			opponentUtility.add(utility);
			int index = 0;
			int size = opponentUtility.size();
			if (opponentUtility.size() > 3) {
				index = opponentUtility.size() - 3;
				size = 3;
			}
			double utilSum = 0.0;
			for (double tmp : opponentUtility.subList(index, opponentUtility.size())) {
				utilSum += tmp;
			}
			avgUtil = utilSum/size;
			opponentAvgUtility.add(avgUtil);
			//beta = calculateBeta(beta);
			coeff = calculateCoeff();
		}
		
		// DEBUG: Print the freq/calc/weights tables to check they are correctly updated
		System.out.println("Frequency Table");
		for (int i=0; i<FreqTable1.size();i++)
	        System.out.println(FreqTable1.get(i));
		
		System.out.println("Calculation Table");
		for (int i=0; i<CalcTable.size();i++)
	        System.out.println(CalcTable.get(i));
		
		System.out.println("Normalized Weights Array");
		for (int i=0; i<NormWeights.size();i++)
	        System.out.println(NormWeights.get(i));
		
		System.out.println("Valuation for this offer: " + utility);
		System.out.println("Average utility: " + avgUtil);
		
		System.out.println("-----------------------------");
	}
	
	@Override
	public String getDescription() 
	{
		return "I am your father.";
	}

	/**
	 * This stub can be expanded to deal with preference uncertainty in a more sophisticated way than the default behavior.
	 */
	@Override
	public AbstractUtilitySpace estimateUtilitySpace() 
	{
		return super.estimateUtilitySpace();
	}


	// Update the Frequency and Calc tables
	private void updateFreqTable(Bid offer) {
		
		int intValue;
		double NumberOfValues;
		double n0;
		
		List< Issue > bidissues = offer.getIssues();
		
		for (Issue issue : bidissues) {
			
			// Iterate over each issue
		    int issueNumber = issue.getNumber();
		    // Issue number starts at 1 but array index starts at 0, hence the -1. 
		    issueNumber = issueNumber - 1;
		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    
		    int valueNumber = 0;
		    // Iterate over each value of the issue
		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		    	// If the specific value for the specific issue was offered, then add 1 to that cell
		    	if (offer.getValue(issueNumber+1).equals(valueDiscrete)) {
		    		intValue = FreqTable1.get(issueNumber).get(valueNumber);
		    		intValue = intValue + 1;			    		
		    		FreqTable1.get(issueNumber).set(valueNumber, intValue);
		    	}
		    	// At this point, FreqTable2 is an exact copy of FreqTable1 
		    	FreqTable2.get(issueNumber).set(valueNumber, FreqTable1.get(issueNumber).get(valueNumber));
		    	valueNumber = valueNumber + 1;
		    }   
		    // Re-arrange Freq 2 so each row is sorted in ascending order. We will use this go get the calc. table
		    FreqTable2.get(issueNumber).sort(null);
		    NumberOfValues = (double)valueNumber;
		    
		    valueNumber = 0;
		    for (int valueD : FreqTable1.get(issueNumber)) {		    	
		    	// The rank of each value is gotten from the index of the value in the Freq2 table (which is sorted)
		    	n0 = (double)FreqTable2.get(issueNumber).lastIndexOf(valueD) + 1;
		    	CalcTable.get(issueNumber).set(valueNumber, (n0 / NumberOfValues));
		    	valueNumber = valueNumber + 1;   	
		    }
		}
	}
	
	// This function updates the Weights array. updateFreqTable must be called before updateWeightsArray.
	private void updateWeightsArray(Bid offer) {
		
		double weight;
		double totalWeight;
		
		List< Issue > bidissues = offer.getIssues();
		weight = 0;
		totalWeight = 0;
		
		for (Issue issue : bidissues) {
			
			// Iterate over each issue
		    int issueNumber = issue.getNumber();
		    // Issue number starts at 1 but array index starts at 0, hence the -1. 
		    issueNumber = issueNumber - 1;
		    
		    Weights.set(issueNumber, 0.0);
		    for (int valueD : FreqTable1.get(issueNumber)) {
		    	weight = (Math.pow(valueD, 2) / Math.pow(NoOfBids, 2)) + Weights.get(issueNumber);
		    	Weights.set(issueNumber, weight);
		    }
		    // TotalWeight variable will be used to normalize the weights
		    totalWeight = totalWeight + weight;
		    
		}
		int issueNumber = 0;
		for (double w : Weights) {
			NormWeights.set(issueNumber, w/totalWeight);
			issueNumber = issueNumber + 1;
		}
	}
	
	// This function calculates the opponent utility for a specific offer based on the frequency of offers
	// updateWeightsArray and updateFreqTable must be called beforehand
	private double getOpponentUtility(Bid offer) {
		
		int issueIndex = 0;
		double valuation = 0;
		
		List< Issue > bidissues = offer.getIssues();
		
		for (Issue issue : bidissues) {
			int issueNumber = issue.getNumber();
		    issueNumber = issueNumber - 1;
		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    
		    // ValueIndex holds each value index of this specific offer. The value on index 0 of the array corresponds to the value # of Issue #1 and so on.
		    // For example: Issue 1 may have 3 different values: Red Yellow Blue
		    // This offer has the value Blue for Issue 1. So ValueIndex[0] = 2; 
		    int valueNumber = 0;
		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		    	if (offer.getValue(issueNumber+1).equals(valueDiscrete)) {
		    		ValueIndex.set(issueNumber, valueNumber);
	    		}
		    	valueNumber = valueNumber + 1;
		    }
		}
		
		//Calculate the valuation using the values from the Norm. Weights array and the Calc. table
		//Get the values from these arrays using the issueIndex and the valueIndex
		for (double w: NormWeights) {
			double temp = CalcTable.get(issueIndex).get(ValueIndex.get(issueIndex));
			valuation = valuation + temp * w;
			issueIndex = issueIndex + 1;
		}
		return valuation;
	}
	
	// C3P0 END
	

}
