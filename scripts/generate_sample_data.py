import csv
import random
import os

def generate_paysim_sample(output_path="data/sample/paysim_sample_5000.csv", num_rows=5000):
    random.seed(42) # Deterministic seed
    
    types = ["PAYMENT", "TRANSFER", "CASH_OUT", "DEBIT", "CASH_IN"]
    
    # Generate customer pools
    customers = [f"C{random.randint(1000000, 9999999)}" for _ in range(1200)]
    merchants = [f"M{random.randint(1000000, 9999999)}" for _ in range(300)]
    
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    with open(output_path, mode='w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow([
            "step", "type", "amount", "nameOrig", 
            "oldbalanceOrg", "newbalanceOrig", 
            "nameDest", "oldbalanceDest", "newbalanceDest", "isFraud"
        ])
        
        for i in range(1, num_rows + 1):
            step = (i // 100) + 1
            txn_type = random.choice(types)
            amount = round(random.uniform(10.0, 50000.0), 2)
            
            orig = random.choice(customers)
            dest = random.choice(merchants) if txn_type == "PAYMENT" else random.choice(customers)
            
            old_orig = round(random.uniform(amount, amount + 100000.0), 2)
            new_orig = max(0.0, round(old_orig - amount, 2))
            
            old_dest = round(random.uniform(0.0, 500000.0), 2)
            new_dest = round(old_dest + amount, 2)
            
            is_fraud = 1 if (txn_type in ["TRANSFER", "CASH_OUT"] and amount > 40000 and random.random() < 0.3) else 0
            
            writer.writerow([
                step, txn_type, amount, orig,
                old_orig, new_orig,
                dest, old_dest, new_dest, is_fraud
            ])

    print(f"Generated {num_rows} PaySim sample rows at {output_path}")

if __name__ == "__main__":
    generate_paysim_sample()
