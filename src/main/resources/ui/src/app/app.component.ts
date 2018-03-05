import {Component, Injectable, OnInit} from '@angular/core';
import {ApiService, Service} from "./services/api.service";

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.css']
})
@Injectable()
export class AppComponent implements OnInit {
    title = 'app';

    constructor(private apiService: ApiService) {
    }

    services: Service[];

    ngOnInit(): void {
        this.apiService.listServices().subscribe((r) => {
            this.services = r
        })
    }
}
