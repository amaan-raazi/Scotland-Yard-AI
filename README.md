# Scotland Yard AI

Sherbot Holmes is an AI for Scotland Yard, developed by me and <a href="github.com/aay-b">Aayush</a> as the open‑ended part of our first‑year OOP assignment at the University of Bristol.

Goal: Build an AI for Mr. X that plays effectively against detectives using minimax search, alpha‑beta pruning, and heuristic evaluation.

---

## **How it Works**
- **Minimax with Alpha-Beta Pruning:**  
  The AI simulates future game states up to a configurable depth  to choose the most promising move for Mr. X.
- **Heuristic Evaluation:**  
  It evaluates positions based on:
    - Distance to the nearest detective.
    - Number of unoccupied adjacent nodes (freedom of movement).
    - Penalties for using special tickets (secret/double moves) or revisiting locations.
- **Precomputed Shortest Paths:**  
  Uses Floyd–Warshall to calculate the shortest paths between all board locations for faster heuristic calculations.
- **Board Proxy:**  
  A proxy class allows simulation of detective and Mr. X moves without mutating the real game state.
---


## **Acknowledgements**
- University of Bristol – first year Object-Oriented Programming module.
- Scotland Yard game framework (provided by the course team).
