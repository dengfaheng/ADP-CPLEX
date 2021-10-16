package com.dfh.ADP;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;

import java.io.*;
import java.util.*;


public class ADPCPLEX {

    //模型输入参数
    private int productNr;
    private int[] productsCap;
    private int commonCap;
    private int T;
    private double lambda;
    private List<List<Double>> productPrices; //产品可选的价格
    private final List<Double> priceWithoutCap;     //无capacity的时候价格
    private double beta;
    private double[] A;

    public final String dir = "";


    //模型集合
    private List<List<Integer>> X;      //state space的枚举
    //this.cplex
    public  IloCplex cplex;

    //solution
    public double cplexCost;
    private final Random r;
    private double startTime;

    public ADPCPLEX(String fileName, Random theR) throws IOException, IloException {
    	this.r = theR;
        int i, j;
        //做相应的io
        BufferedReader br = new BufferedReader(new FileReader(fileName+".txt"));
        String line;

        this.priceWithoutCap = new ArrayList<>();
        this.priceWithoutCap.add(1e10);

        while ((line = br.readLine()) != null){
            if(line.startsWith("+")){
                String[] data;
                for(i = 0; i < 7; ++i){
                    data = br.readLine().split(":");
                    switch (i){
                        case 0:
                            this.productNr = Integer.parseInt(data[1].trim());
                            break;
                        case 1:
                            this.commonCap = Integer.parseInt(data[1].trim());
                            this.productsCap = new int[this.productNr+1];
                            for(j = 1; j <= this.productNr; ++j){
                                this.productsCap[j] = this.commonCap;
                            }
                            break;
                        case 2:
                            this.T = (int)Double.parseDouble(data[1].trim());
                            break;
                        case 3:
                            String lambdaStr = data[1].trim().replace("[", "");
                            lambdaStr = lambdaStr.replace("]", "");
                            this.lambda = Double.parseDouble(lambdaStr);
                            break;
                        case 4:
                            String[] data2 = data[1].split("#");
                            this.productPrices = new ArrayList<>();
                            //System.out.println(data[1]);
                            for(j = 0; j < this.productNr; ++j){
                                String[] data3 = data2[j].trim().split(",");
                                List<Double> price = new ArrayList<>();
                                for (String s : data3) {
                                    price.add(Double.parseDouble(s.trim()));
                                }
                                this.productPrices.add(price);
                            }
                            break;
                        case 5:
                            this.beta = -Double.parseDouble(data[1].trim());
                            break;
                        case 6:
                            this.A = new double[this.productNr + 1];
                            String[] data4 = data[1].trim().split(",");
                            for(j = 0; j < data4.length; ++j){
                                this.A[j+1] = Double.parseDouble(data4[j].trim());
                            }
                            break;
                        default:
                            System.out.println("how this could happen!!!");
                    }
                }

                //初始化数据
                //1. 枚举所有的状态空间
                List<List<Integer>> statesList = new ArrayList<>();
                for(i = 1; i <= this.productNr; ++i){
                    List<Integer> productCapState = new ArrayList<>();
                    for(j = 0; j <= this.productsCap[i]; ++j){
                        productCapState.add(j);
                    }
                    statesList.add(productCapState);
                }
                this.X = getDescartes(statesList);
                System.out.println("状态空间枚举完成，状态数 > "+this.X.size());
                try{
                    this.buildAndSolveModel(line, fileName);
                }catch (OutOfMemoryError e){
                    //把所有数据写出
                    BufferedWriter bw = new BufferedWriter(new FileWriter(dir+fileName+"_out.txt", true));
                    bw.write(line+"\n");
                    bw.write("Constraint number: NA"+"\n");
                    bw.write("Number of Product: "+this.productNr+"\n");
                    bw.write("Initial Capacity : "+this.commonCap+"\n");
                    bw.write("T                : "+this.T+"\n");
                    bw.write("lambda           : "+this.lambda+"\n");
                    StringBuilder pStr = new StringBuilder();
                    for(List<Double> p : this.productPrices){
                        pStr.append(Arrays.toString(p.toArray())).append(", ");
                    }

                    bw.write("Product Price    : "+pStr+"\n");
                    bw.write("beta             : "+this.beta+"\n");
                    List<Double> actualA = new ArrayList<>();
                    for(i = 1; i <= this.productNr; ++i){
                        actualA.add(this.A[i]);
                    }
                    bw.write("a                : "+Arrays.toString(actualA.toArray())+"\n");
                    bw.write("----------- LP solution --------------- \n");
                    bw.write(e.getMessage()+"\n\n");
                    bw.flush();
                    bw.close();
                    e.printStackTrace();
                }finally {
                    System.gc();
                }

            }
        }


        /*
        //读取相应的配置

        this.productNr = 2;
        this.commonCap = 10;
        this.T = 171;
        this.lambda = 0.8736100673527116;
        this.beta = 0.7684284176738447;

        this.A = new double[]{0.0, 0.45388476308579395, 0.759744403139883};

        this.productPrices = new ArrayList<>();
        List<Double> p1 = new ArrayList<>(Arrays.asList(1.99,1.8,1.61));
        List<Double> p2 = new ArrayList<>(Arrays.asList(2.0,1.82,1.61));
        //List<Double> p3 = new ArrayList<>(Arrays.asList(1.99,1.8,1.6));
        this.productPrices.add(p1);
        this.productPrices.add(p2);
        //this.productPrices.add(p3);


        //分配内存空间
        this.productsCap = new int[this.productNr+1];
        for(j = 1; j <= this.productNr; ++j){
            this.productsCap[j] = this.commonCap;
        }
        List<List<Integer>> statesList = new ArrayList<>();
        for(i = 1; i <= this.productNr; ++i){
            List<Integer> productCapState = new ArrayList<>();
            for(j = 0; j <= this.productsCap[i]; ++j){
                productCapState.add(j);
            }
            statesList.add(productCapState);
        }
        this.X = getDescartes(statesList);

        this.buildAndSolveModel("line");
         */
    }





    public void buildAndSolveModel(String lineStr, String fileName) throws IloException, IOException {
        int i, j, t;
        //model
        this.cplex = new IloCplex();
        this.startTime = this.cplex.getCplexTime();
        this.cplex.setParam(IloCplex.Param.Simplex.Tolerances.Optimality, 1e-6);
        //this.cplex.setParam(IloCplex.DoubleParam.TimeLimit, 3600);
        //this.cplex.setOut(null);

        //初始化变量
        //实数变量
        IloNumVar[] theta = new IloNumVar[this.T + 2]; // 1 to T+1
        //实数变量
        IloNumVar[][] v = new IloNumVar[this.T + 2][this.productNr + 1]; // 1 to T+1, 1 to this.productNr
        for(i = 1; i <= this.T + 1; ++i){
            theta[i] = cplex.numVar(0, Double.POSITIVE_INFINITY, IloNumVarType.Float, "theta["+i +"]");
            for(j = 1; j <= this.productNr; ++j){
                v[i][j] = cplex.numVar(0, Double.POSITIVE_INFINITY, IloNumVarType.Float, "V["+ i +"]["+ j +"]");
            }
        }

        //构建目标函数
        IloNumExpr obj = this.cplex.numExpr();

        for(j = 1; j <= this.productNr; ++j){
            obj = this.cplex.sum(obj, this.cplex.prod(this.productsCap[j], v[1][j]));
        }
        obj = this.cplex.sum(obj, theta[1]);

        this.cplex.addMinimize(obj);

        //构建约束
        long constraintsNr = 0;
        IloNumExpr expr1, expr2, expr3;
        for(t = 1; t <= this.T; ++t){
            expr1 = this.cplex.sum(theta[t], this.cplex.prod(-1, theta[t+1]));
            for(List<Integer> x : this.X){
                //System.out.println("x = "+x);
                int sumX = this.listSum(x);
                //获取Rx
                List<List<Double>> Rx = this.R(x);
                for(List<Double> r : Rx){
                    //System.out.println("r = "+r);
                    //获取P
                    List<Double> prob = this.P(r);
                    expr2 = this.cplex.numExpr();
                    double numExpr3 = 0;
                    for(j = 1; j <= this.productNr; ++j){
                        IloNumExpr subExpr1 = this.cplex.prod(v[t][j], x.get(j-1));
                        IloNumExpr subExpr2 = this.cplex.prod(v[t+1][j], -1);
                        double numSubExpr3     = x.get(j-1) - this.lambda * prob.get(j-1);
                        IloNumExpr subExpr4 = this.cplex.prod(subExpr2, numSubExpr3);
                        IloNumExpr subExpr5 = this.cplex.sum(subExpr1, subExpr4);
                        expr2 = this.cplex.sum(expr2, subExpr5);

                        numExpr3 += this.lambda * prob.get(j-1) * r.get(j-1);
                    }
                    /*
                    //TODO
                    if(sumX == 0){
                        expr3 = this.cplex.sum(0, expr2);
                    }else{
                        expr3 = this.cplex.sum(expr1, expr2);
                    }
                     */
                    expr3 = this.cplex.sum(expr1, expr2);
                    constraintsNr++;
                    //if(constraintsNr <= 19) continue;
                    this.cplex.addGe(expr3, numExpr3, "mc"+constraintsNr);
                   // constraintsNr++;
                }
            }
        }

        System.out.println("constraints number > "+constraintsNr);
        //this.cplex.exportModel("./ADP.lp");
        //求解
        if(this.cplex.solve()){
	        //把所有数据写出
	        BufferedWriter bw = new BufferedWriter(new FileWriter(dir+fileName+"_out.txt", true));
	        bw.write(lineStr+"\n");
	        bw.write("Constraint number: "+constraintsNr+"\n");
	        bw.write("Number of Product: "+this.productNr+"\n");
	        bw.write("Initial Capacity : "+this.commonCap+"\n");
	        bw.write("T                : "+this.T+"\n");
	        bw.write("lambda           : "+this.lambda+"\n");
	        StringBuilder pStr = new StringBuilder();
	        for(List<Double> p : this.productPrices){
		        pStr.append(Arrays.toString(p.toArray())).append(", ");
	        }

	        bw.write("Product Price    : "+pStr+"\n");
	        bw.write("beta             : "+this.beta+"\n");
	        List<Double> actualA = new ArrayList<>();
	        for(i = 1; i <= this.productNr; ++i){
		        actualA.add(this.A[i]);
	        }
	        bw.write("a                : "+Arrays.toString(actualA.toArray())+"\n");
	        bw.write("----------- LP solution --------------- \n");

            this.cplexCost = this.cplex.getObjValue();
            System.out.println("objective value = "+this.cplexCost);
            double theta1 = this.cplex.getValue(theta[1]);
            System.out.println("theta_1 = "+theta1);
            double[] V1 = new double[this.productNr+1];
            double[] V1Write = new double[this.productNr];
            for(j = 1; j <= this.productNr; ++j){
                V1[j] = this.cplex.getValue(v[1][j]);
                V1Write[j-1]=V1[j];
                System.out.println("V[1]["+j+"] = " +V1[j]);
            }
            List<Integer> currX = new ArrayList<>();
            for(i = 0; i < this.productNr; ++i){
                currX.add(this.productsCap[i+1]);
            }
            double totalR = 0;
            for(t = 1; t <= this.T; ++t){
                double V1xt = this.V1Func(currX, theta1, V1);
                List<List<Double>> allRx = this.R(currX);
                List<Double> bestR = this.getMaxRt(allRx, currX, theta1, V1);
                List<Double> bestPj = this.P(bestR);
                double sumPj = 0;
                for(double theP : bestPj){
                    sumPj += theP;
                }
                bestPj.add(0, 1-sumPj);
                int ct = this.randSrc(bestPj);
                if(ct != 0){
                    totalR += bestR.get(ct-1);
                    int newCapCt = currX.get(ct - 1) - 1;
                    currX.set(ct-1, newCapCt);
                }
            }
            System.out.println("totalR = "+totalR);

	        bw.write("LP status        : "+this.cplex.getStatus()+"\n");
	        bw.write("LP Time(s)       : "+(this.cplex.getCplexTime()-this.startTime)+"\n");
            bw.write("LP objective     : "+this.cplexCost+"\n");
            bw.write("theta_1          : "+theta1+"\n");
            bw.write("V[1]             : " +Arrays.toString(V1Write)+"\n");
            bw.write("R                : "+totalR+"\n");
            bw.write("\n");
            bw.flush();
            bw.close();
        }else{

            //把所有数据写出
            BufferedWriter bw = new BufferedWriter(new FileWriter(dir+fileName+"_out.txt", true));
            bw.write(lineStr+"\n");
            bw.write("Constraint number: "+constraintsNr+"\n");
            bw.write("Number of Product: "+this.productNr+"\n");
            bw.write("Initial Capacity : "+this.commonCap+"\n");
            bw.write("T                : "+this.T+"\n");
            bw.write("lambda           : "+this.lambda+"\n");
            StringBuilder pStr = new StringBuilder();
            for(List<Double> p : this.productPrices){
                pStr.append(Arrays.toString(p.toArray())).append(", ");
            }

            bw.write("Product Price    : "+pStr+"\n");
            bw.write("beta             : "+this.beta+"\n");
            List<Double> actualA = new ArrayList<>();
            for(i = 1; i <= this.productNr; ++i){
                actualA.add(this.A[i]);
            }
            bw.write("a                : "+Arrays.toString(actualA.toArray())+"\n");
            bw.write("----------- LP solution --------------- \n");

            System.out.println("can not solved!!!");
            this.cplex.end();
            bw.write("cannot find feasible in time limit \n");
            bw.write("\n");
            bw.flush();
            bw.close();
            return;
        }

        this.cplex.end();
    }

    private int randSrc(List<Double>  prob) {
        List<Double> probList = new ArrayList<>();
        for(int i = 0; i < prob.size(); ++i){
            double sumP = 0;
            for(int j = 0; j <= i; ++j){
                sumP += prob.get(j);
            }
            probList.add(sumP);
        }

        double randP = r.nextFloat();
        if(randP <= probList.get(0)) return 0;
        for(int i = 1; i < probList.size(); ++i){
            if(randP <= probList.get(i)) return i;
        }
        return -1;
    }

    private List<Double> getMaxRt(List<List<Double>> Rx, List<Integer> xt, double theta1, double[] V1){
        List<Double> bestRt = null;
        double bestVal = Double.NEGATIVE_INFINITY;
        for(List<Double> theR : Rx){
            List<Double> Pj = this.P(theR);
            double sum1 = 0, sum2 = 0;
            for(int j = 1; j <= this.productNr; ++j){
                List<Integer> newX = new ArrayList<>(xt);
                int newXj = newX.get(j-1)-1;
                newX.set(j-1, newXj);
                sum1 += this.lambda * Pj.get(j-1)*(theR.get(j-1)+this.V1Func(newX, theta1, V1));
            }
            double sumP = 0;
            for(double p : Pj){
                sumP += p;
            }
            sum2 = (this.lambda * (1-sumP) + 1 - this.lambda ) * this.V1Func(xt, theta1, V1);
            if(bestVal < sum1 + sum2) {
                bestVal = sum1 + sum2;
                bestRt = theR;
            }
        }
        return bestRt;
    }

    private double V1Func(List<Integer> x, double theta1, double[] V1){
        double V1xt = theta1;
        for(int j = 1; j <= this.productNr; ++j){
            V1xt += V1[j] * x.get(j-1);
        }
        return V1xt;
    }

    private int listSum(List<Integer> theList){
        int sumInt = 0;
        for(int e : theList){
            sumInt += e;
        }
        return sumInt;
    }



    //输入产品的定价向量，输出选择各产品的概率
    private List<Double> P(List<Double> r){
        List<Double> prob = new ArrayList<>();
        //计算分母
        double sumProb = 1;
        for(int i = 1; i <= this.productNr; ++i){
            sumProb += Math.exp(this.A[i] - this.beta * r.get(i-1));
        }
        for(int j = 1; j <= this.productNr; ++j){
            double pj = Math.exp(this.A[j] - this.beta * r.get(j-1)) / sumProb;
            prob.add(pj);
        }
        return prob;
    }

    //输入各产品的当前状态，输出各个产品的价格的排列组合，当产品无货时，价格为无穷
    private List<List<Double>> R(List<Integer> stateX){
        List<List<Double>> prices = new ArrayList<>();
        for(int i = 0; i < stateX.size(); ++i){
            if(stateX.get(i) == 0){
                prices.add(this.priceWithoutCap);
            }else{
                prices.add(this.productPrices.get(i));
            }
        }
        return getDescartes(prices);
    }

    public static void main(String[] args) throws IloException, IOException {
        System.out.println("running file > "+args[0]);
        Random r = new Random(10);
        String fileName = args[0].split("\\.")[0];
        new ADPCPLEX(fileName, r);
        /*
    	for(int i = 2; i <= 10; ++i){
			for(int j = 10; j <= 100; j += 10){
			    if(i <= 3 && j <= 20) continue;
				String fileName = "test_n"+i+"_inacap"+j;
				new ADPCPLEX(fileName, r);
			}
		}
         */
    }

    public static void main1(String[] args) throws Exception  {
        List<Double> cap1 = new ArrayList<>();
        cap1.add(0.0);
        cap1.add(0.1);
        cap1.add(0.2);
        List<Double> cap2 = new ArrayList<>();
        cap2.add(0.0);
        cap2.add(0.1);
        cap2.add(0.2);
        cap2.add(0.3);
        List<Double> cap3 = new ArrayList<>();
        cap3.add(0.0);
        cap3.add(0.1);
        cap3.add(0.2);
        cap3.add(0.3);
        cap3.add(0.4);

        List<List<Double>> listData = new ArrayList<>();
        listData.add(cap1);
        listData.add(cap2);
        listData.add(cap3);
        List<List<Double>> lisReturn = getDescartes(listData);
        //System.out.println(lisReturn);
        for( List<Double> item : lisReturn)
        {
            System.out.println(item);
        }
    }

    private static <T> List<List<T>> getDescartes(List<List<T>> list) {
        List<List<T>> returnList = new ArrayList<>();
        descartesRecursive(list, 0, returnList, new ArrayList<T>());
        return returnList;
    }

    /**
     * 递归实现
     * 原理：从原始list的0开始依次遍历到最后
     *
     * @param originalList 原始list
     * @param position     当前递归在原始list的position
     * @param returnList   返回结果
     * @param cacheList    临时保存的list
     */
    private static <T> void descartesRecursive(List<List<T>> originalList, int position, List<List<T>> returnList, List<T> cacheList) {
        List<T> originalItemList = originalList.get(position);
        for (int i = 0; i < originalItemList.size(); i++) {
            //最后一个复用cacheList，节省内存
            List<T> childCacheList = (i == originalItemList.size() - 1) ? cacheList : new ArrayList<>(cacheList);
            childCacheList.add(originalItemList.get(i));
            if (position == originalList.size() - 1) {//遍历到最后退出递归
                returnList.add(childCacheList);
                continue;
            }
            descartesRecursive(originalList, position + 1, returnList, childCacheList);
        }
    }
}
