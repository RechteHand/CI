package flappy_bird;

import java.util.ArrayList;
import java.util.List;

public class PopulationManager {
    private List<NeuralNetwork> population;
    private int generation;
    
    public PopulationManager() {
        population = new ArrayList<>();
        for (int i = 0; i < Config.POPULATION_SIZE; i++) {
            population.add(new NeuralNetwork(Config.INPUT_SIZE, Config.HIDDEN_SIZE, Config.OUTPUT_SIZE));
        }
        generation = 1;
    }
    
    public List<NeuralNetwork> getPopulation() {
        return population;
    }
    
    public void evolve(List<Bird> evaluatedBirds) {
        // Sort birds by score descending
        evaluatedBirds.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        
        System.out.println("=== Generation " + generation + " ===");
        System.out.println("Best Score: " + evaluatedBirds.get(0).getScore());
        
        List<NeuralNetwork> nextGen = new ArrayList<>();
        
        // Elitism: keep top 5% unchanged (mindestens 1)
        int eliteCount = Math.max(1, Config.POPULATION_SIZE / 20);
        for (int i = 0; i < eliteCount && nextGen.size() < Config.POPULATION_SIZE; i++) {
            nextGen.add(evaluatedBirds.get(i).getBrain().copy());
        }
        
        // Mutate the rest based on top 20% (mindestens 1)
        int selectionPoolSize = Math.max(1, Config.POPULATION_SIZE / 5);
        java.util.Random random = new java.util.Random();
        
        while (nextGen.size() < Config.POPULATION_SIZE) {
            // Select random elite parent
            Bird parent = evaluatedBirds.get(random.nextInt(selectionPoolSize));
            NeuralNetwork child = parent.getBrain().copy();
            
            // Mutate child
            child.mutate(Config.MUTATION_RATE, Config.MUTATION_STRENGTH);
            nextGen.add(child);
        }
        
        population = nextGen;
        generation++;
    }
    
    public int getGeneration() { return generation; }
}
