import java.util.Random;
public class TestInsertFit {
    public static void main(String[] args) {
        int n = 50;
        int[][] delay = new int[n][n];
        Random rng = new Random(42);
        for(int i=0; i<n; i++)
            for(int j=0; j<n; j++)
                delay[i][j] = rng.nextInt(100);
                
        int[] p = new int[n];
        for(int i=0; i<n; i++) p[i] = i;
        
        int curFit = 0;
        for (int i = 1; i < n; i++) {
            curFit += delay[p[i - 1]][p[i]] * (n - i);
        }
        
        int[] delayPrefix = new int[n];
        delayPrefix[0] = 0;
        for (int k = 1; k < n; k++) {
            delayPrefix[k] = delayPrefix[k-1] + delay[p[k-1]][p[k]];
        }
        
        boolean ok = true;
        for(int from=0; from<n; from++) {
            for(int to=0; to<n; to++) {
                if(from == to) continue;
                int f1 = insertFit(p, from, to, curFit, delay, n);
                int f2 = insertFitConst(p, from, to, curFit, delay, n, delayPrefix);
                if(f1 != f2) {
                    System.out.println("Mismatch at from=" + from + " to=" + to + ": f1=" + f1 + " f2=" + f2);
                    ok = false;
                }
            }
        }
        if(ok) System.out.println("All correct!");
    }
    
    static int insertFit(int[] p, int from, int to, int curFit, int[][] delay, int n) {
        int oldSum = 0, newSum = 0;
        if (from < to) {
            if (from > 0) {
                oldSum += delay[p[from - 1]][p[from]] * (n - from);
                newSum += delay[p[from - 1]][p[from + 1]] * (n - from);
            }
            for (int k = from + 1; k < to; k++) {
                oldSum += delay[p[k - 1]][p[k]] * (n - k);
                newSum += delay[p[k]][p[k + 1]] * (n - k);
            }
            oldSum += delay[p[to - 1]][p[to]] * (n - to);
            newSum += delay[p[to]][p[from]] * (n - to);
            if (to + 1 < n) {
                oldSum += delay[p[to]][p[to + 1]] * (n - to - 1);
                newSum += delay[p[from]][p[to + 1]] * (n - to - 1);
            }
        } else {
            if (to > 0) {
                oldSum += delay[p[to - 1]][p[to]] * (n - to);
                newSum += delay[p[to - 1]][p[from]] * (n - to);
            }
            oldSum += delay[p[to]][p[to + 1]] * (n - to - 1);
            newSum += delay[p[from]][p[to]] * (n - to - 1);
            for (int k = to + 2; k <= from; k++) {
                oldSum += delay[p[k - 1]][p[k]] * (n - k);
                newSum += delay[p[k - 2]][p[k - 1]] * (n - k);
            }
            if (from + 1 < n) {
                oldSum += delay[p[from]][p[from + 1]] * (n - from - 1);
                newSum += delay[p[from - 1]][p[from + 1]] * (n - from - 1);
            }
        }
        return curFit - oldSum + newSum;
    }

	static int insertFitConst(int[] p, int from, int to, int curFit, int[][] delay, int n, int[] delayPrefix) {
		int diff = 0;
		if (from < to) {
			if (from > 0) {
				diff += delay[p[from - 1]][p[from + 1]] * (n - from);
				diff -= delay[p[from - 1]][p[from]] * (n - from);
			}
			diff += delay[p[to]][p[from]] * (n - to);
			if (to + 1 < n) {
				diff += delay[p[from]][p[to + 1]] * (n - to - 1);
				diff -= delay[p[to]][p[to + 1]] * (n - to - 1);
			}
			diff -= delay[p[from]][p[from + 1]] * (n - from - 1);
			
			diff += (delayPrefix[to] - delayPrefix[from + 1]);
		} else {
			if (to > 0) {
				diff += delay[p[to - 1]][p[from]] * (n - to);
				diff -= delay[p[to - 1]][p[to]] * (n - to);
			}
			diff += delay[p[from]][p[to]] * (n - to - 1);
			if (from + 1 < n) {
				diff += delay[p[from - 1]][p[from + 1]] * (n - from - 1);
				diff -= delay[p[from]][p[from + 1]] * (n - from - 1);
			}
			diff -= delay[p[from - 1]][p[from]] * (n - from);
			
			diff -= (delayPrefix[from - 1] - delayPrefix[to]);
		}
		return curFit + diff;
	}
}
