package com.dfh.ADP;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


public class ADPCPLEX {

    //模型输入参数
    private final int productNr;
    private final int[] productsCap;
    private final int T;
    private final double lambda;
    private final List<List<Double>> productPrices; //产品可选的价格
    private final List<Double> priceWithoutCap;     //无capacity的时候价格
    private final double beta;
    private final double[] A;


    //模型集合
    private final List<List<Integer>> X;      //state space的枚举
    //this.cplex
    public  IloCplex cplex;
    private IloNumVar[] theta ;  //实数变量
    private IloNumVar[][]   V ;  //实数变量

    //solution
    public double cplexCost;

    public ADPCPLEX() {
        int i, j;
        //读取相应的配置
        this.productNr = 1;
        this.productsCap = new int[this.productNr+1];
        this.T = 10;
        this.lambda = 1;
        this.productPrices = new ArrayList<>();
        this.beta = 1;
        this.A = new double[productNr+1];

        this.priceWithoutCap = new ArrayList<>();
        this.priceWithoutCap.add(Double.POSITIVE_INFINITY);

        //分配内存空间

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
        for( List<Integer> item : this.X) {
            System.out.println(item);
        }


        //分配内存

    }


    public void buildAndSolveModel() throws IloException {
        int i, j, t;
        //model
        this.cplex = new IloCplex();
        this.cplex.setParam(IloCplex.Param.Simplex.Tolerances.Optimality, 1e-9);
        this.cplex.setParam(IloCplex.DoubleParam.TimeLimit, 7200);
        //this.cplex.setOut(null);

        //初始化变量
        this.theta = new IloNumVar[this.T + 2]; // 1 to T+1
        this.V     = new IloNumVar[this.T + 2][this.productNr + 1]; // 1 to T+1, 1 to this.productNr
        for(i = 1; i <= this.T + 1; ++i){
            this.theta[i] = cplex.numVar(0, Double.POSITIVE_INFINITY, IloNumVarType.Float, "theta["+i +"]");
            for(j = 1; j <= this.productNr; ++j){
                this.V[i][j] = cplex.numVar(0, Double.POSITIVE_INFINITY, IloNumVarType.Float, "V["+ i +"]["+ j +"]");
            }
        }

        //构建目标函数
        IloNumExpr obj = this.cplex.numExpr();

        for(j = 1; j <= productNr; ++j){
            obj = this.cplex.sum(obj, this.cplex.prod(this.productsCap[j], this.V[1][j]));
        }
        obj = this.cplex.sum(obj, this.theta[1]);

        this.cplex.addMinimize(obj);

        //构建约束
        long constraintsNr = 0;
        IloNumExpr expr1, expr2, expr3;
        for(t = 1; t <= this.T; ++t){
            expr1 = this.cplex.sum(this.theta[t], this.cplex.prod(-1, this.theta[t+1]));
            for(List<Integer> x : this.X){
                int sumX = this.listSum(x);
                //获取Rx
                List<List<Double>> Rx = this.R(x);
                for(List<Double> r : Rx){
                    //获取P
                    List<Double> prob = this.P(r);
                    expr2 = this.cplex.numExpr();
                    double numExpr3 = 0;
                    for(j = 1; j <= this.productNr; ++j){
                        IloNumExpr subExpr1 = this.cplex.prod(this.V[t][j], x.get(j-1));
                        IloNumExpr subExpr2 = this.cplex.prod(this.V[t+1][j], -1);
                        double numSubExpr3     = x.get(j-1) - this.lambda * prob.get(j-1);
                        IloNumExpr subExpr4 = this.cplex.prod(subExpr2, numSubExpr3);
                        IloNumExpr subExpr5 = this.cplex.sum(subExpr1, subExpr4);
                        expr2 = this.cplex.sum(expr2, subExpr5);

                        numExpr3 += this.lambda * prob.get(j-1) * r.get(j-1);
                    }

                    if(sumX == 0){
                        expr3 = this.cplex.sum(0, expr2);
                    }else{
                        expr3 = this.cplex.sum(expr1, expr2);
                    }
                    this.cplex.addGe(expr3, numExpr3);
                    constraintsNr++;
                }
            }
        }

        System.out.println("constraints number > "+constraintsNr);

        //求解
        if(this.cplex.solve()){
            this.cplexCost = this.cplex.getObjValue();
            System.out.println("objective value = "+this.cplexCost);
        }else{
            System.out.println("can not solved!!!");
        }

        this.cplex.end();
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

    //输入各产品的当前状态，输出各个产品的价格，当产品无货时，价格为无穷
    private List<List<Double>> R(List<Integer> stateX){
        List<List<Double>> prices = new ArrayList<>();
        for(int i = 0; i < stateX.size(); ++i){
            if(stateX.get(i) == 0){
                prices.add(this.priceWithoutCap);
            }else{
                prices.add(this.productPrices.get(i));
            }
        }
        return prices;
    }

    public void outSolution() throws IloException {

    }

    public static void main(String[] args) throws IloException {
        ADPCPLEX adpCplex = new ADPCPLEX();
        adpCplex.buildAndSolveModel();


    }

    public static void main2(String[] args) throws Exception  {
        List<Integer> cap1 = new ArrayList<>();
        cap1.add(0);
        cap1.add(1);
        cap1.add(2);
        List<Integer> cap2 = new ArrayList<>();
        cap2.add(0);
        cap2.add(1);
        cap2.add(2);
        cap2.add(3);
        List<Integer> cap3 = new ArrayList<>();
        cap3.add(0);
        cap3.add(1);
        cap3.add(2);
        cap3.add(3);
        cap3.add(4);

        List<List<Integer>> listData = new ArrayList<>();
        listData.add(cap1);
        //listData.add(cap2);
        //listData.add(cap3);
        List<List<Integer>> lisReturn = getDescartes(listData);
        //System.out.println(lisReturn);
        for( List<Integer> item : lisReturn)
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
