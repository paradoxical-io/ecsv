import {Component, Injectable, OnInit} from '@angular/core';
import {ApiService, Cluster, Service} from "./services/api.service";
import {Observable} from "rxjs/Rx";

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.css']
})
@Injectable()
export class AppComponent implements OnInit {
    constructor(private apiService: ApiService) {
    }

    private allServices: Service[];

    services: Service[];
    clusters: Cluster[];

    currentCluster: string = "default"

    loaded = false;

    showOnlyNonZero: boolean = false;

    currentSearchText: string = "";

    ngOnInit(): void {
        Observable.combineLatest(
            this.apiService.listServices(this.currentCluster),
            this.apiService.listClusters()
        )
            .subscribe((data) => {
                this.allServices = data[0];

                this.services = this.allServices;

                this.clusters = data[1];

                this.loaded = true;
            });
    }

    toggleNonZero(): void {
        this.showOnlyNonZero = !this.showOnlyNonZero;

        this.applyFilters()
    }

    filterNonZero(): void {
        if(this.showOnlyNonZero) {
            this.services = this.services.filter(s => s.desired > 0)
        }
    }

    private applyFilters(): void {
        if (this.currentSearchText.length == 0) {
            this.services = this.allServices;
        } else {
            this.services = this.allServices.filter(s => s.name.indexOf(this.currentSearchText) != -1, this);
        }

        this.filterNonZero()
    }

    filterServices(searchText: string): void {
        this.currentSearchText = searchText;

        this.applyFilters()
    }

    selectCluster(e): void {
        this.currentCluster = e.value;

        this.apiService.listServices(e.value).subscribe(data => this.services = data)
    }
}
